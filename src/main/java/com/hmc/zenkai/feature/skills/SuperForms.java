package com.hmc.zenkai.feature.skills;

import com.hmc.zenkai.feature.Race;
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
 * Habilidad "super_forms": desbloquea las transformaciones de TU raza, una por nivel.
 * Nada de esto está en el JSON de la skill, y es a propósito: el nivel máximo y el coste de
 * cada nivel se DERIVAN de la cadena de formas del datapack. Consecuencias:
 *  - Un saiyan ve 5 niveles (1 + 4 formas) y un arcosiano 6 (1 + 5). Nadie compra un nivel
 *    que no le da nada, que era justo el problema de tener un max_level fijo.
 *  - El coste de cada nivel es el tp_cost de la forma que desbloquea: los números viven en
 *    UN sitio (el JSON de la forma) y no pueden desincronizarse.
 *  - Añadir una forma al datapack alarga la habilidad sola, sin tocar Java ni la skill.
 * Mapa de niveles:
 *   nivel 1 -> BASE. REGALADO (0 TP): se otorga al elegir raza y sobrevive al respec, así que
 *              el jugador SIEMPRE tiene la habilidad. No desbloquea ninguna transformación.
 *   nivel 2 -> 1ª forma · nivel 3 -> 2ª forma · ... · nivel N -> forma N-1.
 * La API base va por Race (no por Player) porque el respec la necesita desde el attachment,
 * que no tiene referencia al jugador.
 */
public final class SuperForms {
    private SuperForms() {}

    public static final String SKILL = "super_forms";

    // ── API por raza ─────────────────────────────────────────────────────────

    /** Cadena de formas de esa raza, en orden. Vacía si la raza no transforma. */
    public static List<ResourceLocation> chain(Race race) {
        return FormRegistry.chainFor(race);
    }

    /**
     * Cadena "comprable" con TP normal de super_forms: la cadena completa cortada en el
     * primer peldaño divine_tier (ver DivineForms) — a partir de ahí, esa rama ya no la cobra
     * ni la cuenta esta skill, sino "god_ki". Sin esto, la última forma de Humano/Namekiano/
     * Majin (y las tres del Saiyan) seguirían comprándose como un rango más de super_forms.
     */
    private static List<ResourceLocation> purchasableChain(Race race) {
        List<ResourceLocation> out = new java.util.ArrayList<>();
        for (ResourceLocation id : chain(race)) {
            FormDef d = FormDef.get(id);
            if (d != null && d.divineTier()) break;
            out.add(id);
        }
        return out;
    }

    /** Niveles que la habilidad puede llegar a tener para esa raza (1 + nº de formas
     *  "mortales", sin contar la rama divina). */
    public static int maxLevel(Race race) {
        return 1 + purchasableChain(race).size();
    }

    /**
     * Coste en TP de comprar ese nivel. El nivel 1 es gratis (es la base); del 2 en adelante,
     * el tp_cost de la forma que desbloquea. MAX_VALUE si ese nivel no existe para esta raza,
     * de modo que nunca sea comprable ni reembolsable.
     */
    public static int tpCostForLevel(Race race, int level) {
        if (level <= 1) return 0;
        List<ResourceLocation> c = purchasableChain(race);
        int i = level - 2;
        if (i >= c.size()) return Integer.MAX_VALUE;
        FormDef fd = FormDef.get(c.get(i));
        return fd == null ? Integer.MAX_VALUE : fd.tpCost();
    }

    /** Posición (1-based) de una forma en la cadena COMPRABLE con super_forms. 0 si no pertenece
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
     * Nivel de super_forms que hace falta tener ANTES de poder empezar una rama divina: el de su
     * antepasado mortal más cercano (human_god cuelga de human_ultra: nivel 3, sin exigir la
     * cima mortal indomitable; ssj_god cuelga de ssj4: el máximo de siempre). Antes era siempre
     * maxLevel, lo que obligaba a agotar una cadena mortal aunque la divina fuera una rama
     * paralela a su final.
     */
    public static int mortalLevelNeededForDivine(Race race, ResourceLocation divineForm) {
        FormDef d = FormDef.get(divineForm);
        ResourceLocation parent = d == null ? null : d.parent();
        while (parent != null) {
            FormDef pd = FormDef.get(parent);
            if (pd == null) break;
            if (!pd.divineTier()) {
                int lvl = requiredLevel(race, parent);
                return lvl == Integer.MAX_VALUE ? maxLevel(race) : lvl;
            }
            parent = pd.parent();
        }
        return maxLevel(race);
    }

    /** Nivel de habilidad necesario para usar esa forma (profundidad + 1, por el nivel base). */
    public static int requiredLevel(Race race, ResourceLocation form) {
        int d = depthOf(race, form);
        return d <= 0 ? Integer.MAX_VALUE : d + 1;
    }

    // ── API por jugador ──────────────────────────────────────────────────────

    /** Nivel actual. 0 solo si aún no se le ha otorgado el nivel base. */
    public static int level(Player p) {
        return SkillEffects.level(p, SKILL);
    }

    public static Race raceOf(Player p) {
        PlayerStatsAttachment att = PlayerStatsAttachment.get(p);
        return att.getRace();
    }

    public static int maxLevel(Player p) {
        return maxLevel(raceOf(p));
    }

    public static int tpCostForLevel(Player p, int level) {
        return tpCostForLevel(raceOf(p), level);
    }

    /**
     * ¿Este jugador tiene desbloqueada esa forma? BASE siempre sí.
     * SSJ4 es la ÚNICA excepción de "solo el nivel importa": además de comprar su nivel con TP
     * hace falta haber completado una vez el ritual de Oozaru (readyForSuperOozaru y
     * readyForSsj4Ritual), y la forma no queda desbloqueada/visible en la rueda hasta entonces (ver
     * PlayerStatsAttachment.hasSsj4Ritual, marcado por PlayerFormAttachment tras el hold
     * Super Oozaru -> SSJ4). El nivel comprado sigue haciendo falta aparte: si se pierde por
     * respec, hay que volver a comprarlo aunque el ritual ya esté hecho — no hay que repetir
     * el ritual, solo el pago.
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
        return level(p) >= requiredLevel(race, form);
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