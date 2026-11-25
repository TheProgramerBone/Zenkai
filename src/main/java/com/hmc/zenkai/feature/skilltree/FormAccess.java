package com.hmc.zenkai.feature.skilltree;

import com.hmc.zenkai.feature.Race;
import com.hmc.zenkai.feature.skills.DivineForms;
import com.hmc.zenkai.feature.skills.EndgamePaths;
import com.hmc.zenkai.feature.skills.SkillEffects;
import com.hmc.zenkai.feature.forms.FormDef;
import com.hmc.zenkai.feature.forms.FormIds;
import com.hmc.zenkai.feature.forms.FormRegistry;
import com.hmc.zenkai.feature.player.PlayerFormAttachment;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.registry.ZenkaiDataAttachments;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Acceso a las transformaciones de TU raza: qué formas posee y puede usar el jugador. Antes era la skill
 * {@code super_forms} (un nivel por peldaño); desde la 0.7.0 (migración v5 del árbol) cada peldaño de la cadena mortal
 * es un NODO PROPIO del skill tree (id del nodo = id de la forma, como las divinas y las ramas de addon) y esa compra es
 * la fuente de verdad. Lo que sigue DERIVÁNDOSE de los datos es la cadena y el coste:
 *  - La cadena mortal (purchasableChain) sale de los {@code parent} del datapack, cortada en el primer peldaño divine_tier.
 *  - El coste de cada nodo es el {@code tp_cost} de su forma: los números viven en UN sitio. El MIND que ocupa va en el
 *    {@code mind_req} del JSON de su nodo ({@code zenkai_skill_tree/<forma>.json}).
 *  - Añadir una forma al datapack alarga la cadena sola.
 * Misma API que tenía FormAccess (unlocked, usable, unlockedChain, depthOf, resolveSibling, legendaryBlocked...), sin nivel
 * ni máximo ni coste por nivel.
 */
public final class FormAccess {
    private FormAccess() {}

    // ── API por raza ─────────────────────────────────────────────────────────

    /** Cadena de formas de esa raza, en orden. Vacía si la raza no transforma. */
    public static List<ResourceLocation> chain(Race race) {
        return FormRegistry.chainFor(race);
    }

    /**
     * Cadena mortal (la que se compra con TP normal en el árbol): la cadena completa cortada en el
     * primer peldaño divine_tier (ver DivineForms) — a partir de ahí, esa rama es de "god_ki" y se
     * compra como nodo divino.
     */
    public static List<ResourceLocation> purchasableChain(Race race) {
        List<ResourceLocation> out = new java.util.ArrayList<>();
        for (ResourceLocation id : chain(race)) {
            FormDef d = FormDef.get(id);
            if (d != null && d.divineTier()) break;
            out.add(id);
        }
        return out;
    }

    /** Posición (1-based) de una forma en la cadena comprable (mortal). 0 si no pertenece
     *  (ni las ramas por maestría). */
    public static int depthOf(Race race, ResourceLocation form) {
        if (race == null || form == null || FormIds.BASE.equals(form)) return 0;
        List<ResourceLocation> c = purchasableChain(race);
        for (int i = 0; i < c.size(); i++) {
            if (c.get(i).equals(form)) return i + 1;
        }
        // Hermana de alineamiento fuera de la cadena (majin_pure_chaos si la cadena eligió a
        // harmony): comparte peldaño con la que SÍ está en ella.
        FormDef d = FormDef.get(form);
        if (d != null && d.alignmentBranch() != FormDef.AlignBranch.NONE && d.parent() != null) {
            for (int i = 0; i < c.size(); i++) {
                FormDef cd = FormDef.get(c.get(i));
                if (cd != null && d.parent().equals(cd.parent())
                        && cd.alignmentBranch() != FormDef.AlignBranch.NONE) return i + 1;
            }
        }
        return 0;
    }

    /**
     * Entre hermanas mortales con {@code alignment_branch} (mismo parent), la que le corresponde
     * a este jugador: GOOD con alineamiento >= 0, EVIL con negativo. Si {@code form} no tiene
     * bifurcación se devuelve tal cual. Mismo papel que DivineForms.resolveForPlayer, pero para
     * formas no divinas.
     */
    public static ResourceLocation resolveSibling(Player p, ResourceLocation form) {
        FormDef d = FormDef.get(form);
        if (d == null || d.alignmentBranch() == FormDef.AlignBranch.NONE || d.parent() == null) return form;
        FormDef.AlignBranch want = PlayerStatsAttachment.get(p).getAlignment() >= 0
                ? FormDef.AlignBranch.GOOD : FormDef.AlignBranch.EVIL;
        for (ResourceLocation sib : FormRegistry.childrenOf(d.parent())) {
            FormDef sd = FormDef.get(sib);
            if (sd != null && sd.alignmentBranch() == want && sd.allows(d.races().iterator().next())) return sib;
        }
        return form;
    }

    /**
     * BLOQUEO LEGENDARIO (decisión del usuario, 2026-10-25): un Saiyan Legendario sigue la cadena de la película
     * (Ikari -> SSJ1 -> Legendario -> Pleno) y NO puede USAR SSJ2 ni SSJ3. Solo se bloquea el USO (rueda, tecla de
     * transformar, mantener la forma puesta): {@link #unlocked} sigue dando true si se compró, porque SSJ4 y los
     * dioses cuelgan de SSJ3 y el progreso no puede romperse (los peldaños se siguen comprando en el árbol aunque
     * no se puedan llevar puestos).
     */
    public static boolean legendaryBlocked(Player p, ResourceLocation form) {
        if (!FormIds.SSJ2.equals(form) && !FormIds.SSJ3.equals(form)) return false;
        PlayerStatsAttachment st = PlayerStatsAttachment.get(p);
        return st.getRace() == Race.SAIYAN && st.isLegendary();
    }

    /** ¿Puede el jugador LLEVAR PUESTA esta forma ahora? = desbloqueada y no bloqueada por la sangre legendaria. */
    public static boolean usable(Player p, ResourceLocation form) {
        return unlocked(p, form) && !legendaryBlocked(p, form);
    }

    /** ¿Cumple el jugador cada maestría mínima declarada en {@code mastery_reqs} de la forma? */
    public static boolean masteryReqsMet(Player p, FormDef def) {
        if (def == null || def.masteryReqs().isEmpty()) return true;
        PlayerFormAttachment fm = p.getData(ZenkaiDataAttachments.PLAYER_FORM.get());
        for (var e : def.masteryReqs().entrySet()) {
            if (fm.getFormMastery(e.getKey()) < e.getValue()) return false;
        }
        return true;
    }

    /**
     * ¿Posee el jugador el antepasado mortal más cercano de esta forma divina? (human_god cuelga de human_ultra: basta con
     * él, sin exigir la cima mortal indomitable; ssj_god cuelga de ssj3.) Sin antepasado mortal en la cadena comprable se
     * exige la cadena entera. Antes era un nivel de super_forms ({@code mortalLevelNeededForDivine}).
     */
    public static boolean mortalAncestorOwned(Player p, ResourceLocation divineForm) {
        Race race = raceOf(p);
        FormDef d = FormDef.get(divineForm);
        ResourceLocation parent = d == null ? null : d.parent();
        while (parent != null) {
            FormDef pd = FormDef.get(parent);
            if (pd == null) break;
            if (!pd.divineTier()) {
                if (depthOf(race, parent) > 0) return ownsStep(p, parent);
                break;
            }
            parent = pd.parent();
        }
        for (ResourceLocation id : purchasableChain(race)) if (!ownsStep(p, id)) return false;
        return true;
    }

    /**
     * ¿Posee el jugador el PELDAÑO mortal de esta forma? Su nodo, o el de una hermana de alineamiento (majin_harmony /
     * majin_pure_chaos comparten peldaño: comprada una, el peldaño es suyo y {@link #unlocked} decide cuál puede usar).
     */
    public static boolean ownsStep(Player p, ResourceLocation form) {
        var tree = PlayerStatsAttachment.get(p).skillTree();
        if (tree.owns(form)) return true;
        FormDef d = FormDef.get(form);
        if (d == null || d.alignmentBranch() == FormDef.AlignBranch.NONE || d.parent() == null) return false;
        for (ResourceLocation sib : FormRegistry.childrenOf(d.parent())) {
            FormDef sd = FormDef.get(sib);
            if (sd != null && sd.alignmentBranch() != FormDef.AlignBranch.NONE && tree.owns(sib)) return true;
        }
        return false;
    }

    /**
     * Concede los primeros {@code steps} peldaños de la cadena mortal de la raza del jugador SIN cobrar TP (comandos, pruebas,
     * migración). {@code waived}: sin ocupar MIND (despertares y migración de una exención antigua). Devuelve cuántos nodos
     * nuevos concedió. No sincroniza: lo hace quien llama.
     */
    public static int grantChain(PlayerStatsAttachment att, int steps, boolean waived) {
        List<ResourceLocation> chain = purchasableChain(att.getRace());
        int n = 0;
        for (int i = 0; i < steps && i < chain.size(); i++) {
            if (!att.skillTree().owns(chain.get(i))) n++;
            att.skillTree().setOwned(chain.get(i), 1);
            if (waived) att.skillTree().setMindWaived(chain.get(i), true);
        }
        return n;
    }

    // ── API por jugador ──────────────────────────────────────────────────────

    public static Race raceOf(Player p) {
        PlayerStatsAttachment att = PlayerStatsAttachment.get(p);
        return att.getRace();
    }

    /**
     * ¿Este jugador tiene desbloqueada esa forma? BASE siempre sí.
     * SSJ4 es la ÚNICA excepción de "solo el nodo importa": además de poseer su nodo
     * hace falta haber completado una vez el ritual de Oozaru (readyForSuperOozaru y
     * readyForSsj4Ritual), y la forma no queda desbloqueada/visible en la rueda hasta entonces (ver
     * PlayerStatsAttachment.hasSsj4Ritual, marcado por PlayerFormAttachment tras el hold
     * Super Oozaru -> SSJ4). El nodo sigue haciendo falta aparte: aunque el ritual ya esté hecho
     * (flag persistente), sin el nodo no hay forma.
     */
    public static boolean unlocked(Player p, ResourceLocation form) {
        if (form == null || FormIds.BASE.equals(form)) return true;
        Race race = raceOf(p);
        FormDef def = FormDef.get(form);
        // Mystic (Potential Unlock): fuera de la cadena, así que sin esto caería en "depth <= 0 ->
        // true" y cualquiera podría elegirla en la rueda. Su acceso es la HABILIDAD comprada (el
        // gate de alineamiento se comprueba al comprarla, ver PotentialUnlock.canPurchase).
        if (FormIds.POTENTIAL_UNLOCK.equals(form)) {
            return SkillEffects.level(p, SkillEffects.POTENTIAL_UNLOCK) > 0;
        }
        // Rama de addon (ver FormRegistry.isAddonBranch): nodo propio comprado y con su padre desbloqueado; independiente de
        // sus hermanas (que pueden venir de otro datapack). Antes caía en «depth 0 → true» y la tenía cualquiera.
        if (FormRegistry.isAddonBranch(def)) {
            return def.allows(race) && PlayerStatsAttachment.get(p).skillTree().owns(form) && unlocked(p, def.parent());
        }
        if (def != null && def.endgamePath()) return EndgamePaths.unlocked(p, form);
        if (def != null && def.divineTier()) return DivineForms.unlocked(p, form);
        // Línea del Legendario: solo un Saiyan al que Shenlong se lo concedió (isLegendary). El resto del desbloqueo
        // (padre desbloqueado + mastery_reqs) es el de cualquier rama por maestría, justo debajo. ssj_green lleva un
        // mastery_reqs trivial (SSJ1 al 0 %) A PROPÓSITO: FormRegistry.nextFrom salta las formas con mastery_reqs al
        // elegir el siguiente peldaño de la cadena, y sin él ssj_green (hijo de ssj1) podía ganarle el sitio a ssj2.
        // Las formas Legendarias de las demás razas siguen la misma regla: solo su raza y solo con el flag.
        Race legendOwner = com.hmc.zenkai.feature.forms.LegendaryForms.ownerOf(form);
        if (legendOwner != null) {
            if (race != legendOwner || !PlayerStatsAttachment.get(p).isLegendary()) return false;
        }
        if (!masteryReqsMet(p, def)) return false;
        if (depthOf(race, form) <= 0) {
            // Rama fuera de la cadena que se desbloquea por maestría (golden_fifth_form...): con
            // el padre desbloqueado y sus mastery_reqs cumplidos basta; no se compra con TP.
            if (def != null && !def.masteryReqs().isEmpty() && def.parent() != null) {
                return unlocked(p, def.parent());
            }
            return true;
        }
        if (def != null && !form.equals(resolveSibling(p, form))) return false;
        if (FormIds.SSJ4.equals(form) && !PlayerStatsAttachment.get(p).hasSsj4Ritual()) return false;
        return ownsStep(p, form);
    }

    /**
     * ¿Puede este saiyan controlar la mutación Oozaru -> Super Oozaru (ver
     * OozaruSystem/PlayerFormAttachment)? Basta con tener SSJ1 (decisión del usuario,
     * 2026-10-21; diseño §3 de .claude/archivo/2026-10-22-pendientes-0.6.0-previo/skill-tree-transformaciones.md). Antes pedía super_forms al nivel
     * MÁXIMO, SSJ4 incluido: había que comprar SSJ4 a ciegas para poder hacer el ritual que lo
     * revela, y por eso el árbol no podía esconder SSJ4.
     */
    public static boolean readyForSuperOozaru(Player p) {
        return unlocked(p, FormIds.SSJ1);
    }

    /**
     * ¿Puede arrancar el último tramo del ritual (Super Oozaru -> SSJ4)? Hace falta SSJ3, el
     * padre real de SSJ4 (decisión del usuario, 2026-10-21): quien llega a Super Oozaru con solo
     * SSJ1 se queda ahí. Completarlo transforma en SSJ4 esa vez y marca el ritual; para volver a
     * usar SSJ4 después hay que COMPRARLO en el árbol (unlocked() pide nivel + ritual).
     */
    public static boolean readyForSsj4Ritual(Player p) {
        return FormRegistry.get(FormIds.SSJ4) != null && unlocked(p, FormIds.SSJ3);
    }

    /**
     * IDs de todas las formas de la raza de este jugador que ya tiene desbloqueadas, en orden
     * de la cadena, con BASE siempre primero. Mismo recorrido BFS por el árbol real
     * (FormRegistry.childrenOf, no el camino único de chain()/nextFrom) que ya usa
     * WheelMenu.forms() para la rueda de transformación — duplicado a propósito en vez de
     * compartido: la rueda construye WheelNode con colores/traducciones/estado de selección
     * propios, esto solo necesita la lista plana de IDs.
     * Usado por ShadowTrainingScreen para el selector de "forma del rival" (simulación de PL
     * vía PlayerStatsAttachment.getPowerLevelWithStatMultiplier(), ver ShadowTrainingManager) —
     * NO transforma al jugador, solo decide qué opciones puede simular.
     */
    public static List<ResourceLocation> unlockedChain(Player p) {
        List<ResourceLocation> out = new ArrayList<>();
        out.add(FormIds.BASE);
        Race race = raceOf(p);

        Set<ResourceLocation> seen = new HashSet<>();
        Deque<ResourceLocation> queue = new ArrayDeque<>();
        ResourceLocation first = FormRegistry.firstFormFor(race);
        if (first != null) queue.add(first);
        while (!queue.isEmpty()) {
            ResourceLocation id = queue.poll();
            if (!seen.add(id)) continue; // corta ciclos de un datapack roto
            if (unlocked(p, id)) out.add(id);
            for (ResourceLocation child : FormRegistry.childrenOf(id)) {
                FormDef cd = FormDef.get(child);
                if (cd != null && cd.allows(race)) queue.add(child);
            }
        }
        return out;
    }
}