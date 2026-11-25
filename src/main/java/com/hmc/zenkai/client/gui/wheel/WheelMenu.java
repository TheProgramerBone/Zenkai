package com.hmc.zenkai.client.gui.wheel;

import com.hmc.zenkai.event.tick.OverlayMetersSystem;
import com.hmc.zenkai.feature.Race;
import com.hmc.zenkai.feature.forms.FormDef;
import com.hmc.zenkai.feature.forms.FormIds;
import com.hmc.zenkai.feature.forms.FormRegistry;
import com.hmc.zenkai.feature.forms.KaiokenTier;
import com.hmc.zenkai.feature.player.PlayerFormAttachment;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.feature.player.PlayerVisualAttachment;
import com.hmc.zenkai.feature.skills.SkillToggles;
import com.hmc.zenkai.registry.ZenkaiDataAttachments;
import com.hmc.zenkai.feature.skills.SkillEffects;
import com.hmc.zenkai.feature.skilltree.FormAccess;
import com.hmc.zenkai.client.gui.ZenkaiPalette;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Construye el árbol de la rueda. ÚNICO sitio donde se decide qué aparece y en qué orden.
 * La rueda NO transforma: solo ELIGE.
 *  - Formas: fija la forma objetivo. Después se sube con la tecla de transformar.
 *  - Kaioken: interruptor on/off. Con él puesto, la tecla de transformar sube escalones de
 *    kaioken (x2, x3...) en vez de la cadena de formas. Apagarlo NO quita el kaioken activo,
 *    solo devuelve la tecla a la escalera normal.
 * Los toggles futuros (mystic, ki fist, ki infuse) son hojas de la raíz igual que Kaioken:
 * se añaden aquí, la pantalla no se entera.
 */
public final class WheelMenu {
    private WheelMenu() {}

    public static final String KAIOKEN_SKILL = "kaioken";

    // Paleta común. La rueda vive sobre el mundo, así que usa la escala CLARA de la paleta,
    // no la tierra. Antes tenía cuatro colores propios —incluido un tercer verde para
    // "encendido" distinto del verde de las formas— que no aparecían en ninguna otra pantalla.
    private static final int COL_FORMS    = ZenkaiPalette.OK;
    private static final int COL_ON       = ZenkaiPalette.OK;      // interruptor encendido
    private static final int COL_OFF      = ZenkaiPalette.DENIED;  // interruptor apagado
    private static final int COL_CATEGORY = ZenkaiPalette.MAXED;   // submenús de interruptores
    private static final int COL_DESCEND  = ZenkaiPalette.DENIED;  // "Descender": acción, no interruptor

    public static WheelNode build(Player p) {
        List<WheelNode> roots = new ArrayList<>();

        // "Forms" envuelve una sub-rama por nivel (Race/Super Forms/Divine, ver forms()) —
        // WheelScreen ya soporta profundidad arbitraria (0.3.0, ver su javadoc/hoverPath), así
        // que anidar una categoría dentro de otra vuelve a funcionar: Forms -> Race -> hoja de
        // forma navega en 3 anillos sin problema. Ya NO incluye "Base": destransformar del
        // en cualquier caso es la tecla de transformar (toque corto), no una opción que valga la pena
        // ocupar sitio en la rueda (pedido del usuario, 2026-09-28).
        List<WheelNode> forms = forms(p);
        if (!forms.isEmpty()) {
            roots.add(WheelNode.category(
                    Component.translatable("wheel.zenkai.forms"), COL_FORMS, forms));
        }

        WheelNode kaioken = kaiokenToggle(p);
        if (kaioken != null) roots.add(kaioken);

        WheelNode descend = descendNode(p);
        if (descend != null) roots.add(descend);

        WheelNode tailMode = tailStyleToggle(p);
        if (tailMode != null) roots.add(tailMode);

        // Ultra Instinct / Ultra Ego: ya NO se reparten entre un interruptor suelto en la
        // raíz y una forma enterrada dentro de "Forms" — cada una es su propia rama con
        // "Technique" (el interruptor on/off) y "Form" (la transformación de verdad), pedido
        // del usuario 2026-09-28. Por eso se excluyen sus ids del bucle genérico de
        // SkillToggles de abajo: si no, aparecerían DOS veces.
        WheelNode ultraInstinct = endgameBranch(p, SkillEffects.ULTRA_INSTINCT,
                Component.translatable("wheel.zenkai.ultra_instinct_branch"), FormIds.ULTRA_INSTINCT_SIGN);
        if (ultraInstinct != null) roots.add(ultraInstinct);

        WheelNode ultraEgo = endgameBranch(p, SkillEffects.ULTRA_EGO,
                Component.translatable("wheel.zenkai.ultra_ego_branch"), FormIds.ULTRA_EGO);
        if (ultraEgo != null) roots.add(ultraEgo);

        // Carpeta "Potential Unlock" (pedido del usuario): agrupa la ruta Mystic. "Mystic" es la
        // forma potential_unlock (misma habilidad) y Beast, su evolución, cuelga a su lado cuando
        // la furia la desbloquea. Ultimate Mystic ya no existe (2026-10-06). No es una rama
        // "divina": es una evolución propia, como Ultra Instinct/Ultra Ego.
        WheelNode potentialUnlock = potentialUnlockBranch(p);
        if (potentialUnlock != null) roots.add(potentialUnlock);

        // Interruptores: los sueltos cuelgan de la raíz, los que declaran categoría se
        // agrupan en su propia rama. Agrupar es SOLO presentación: el packet manda el mismo
        // kind TOGGLE con el mismo id, esté donde esté la hoja.
        Map<String, List<WheelNode>> categories = new LinkedHashMap<>();
        for (SkillToggles.Toggle t : SkillToggles.all()) {
            if (SkillEffects.ULTRA_INSTINCT.equals(t.id()) || SkillEffects.ULTRA_EGO.equals(t.id())) continue;
            // Las armas de ki solo salen en la rueda si están ENCENDIDAS (el usuario, 2026-11-01): encenderlas y
            // apagarlas se hace desde el árbol («Mis habilidades»); aquí servirían para cambiar o apagar la activa.
            if (SkillToggles.CAT_KI_WEAPONS.equals(t.category()) && !SkillToggles.isOn(p, t.id())) continue;
            WheelNode n = toggleNode(p, t.id());
            if (n == null) continue;
            if (t.category() == null) roots.add(n);
            else categories.computeIfAbsent(t.category(), k -> new ArrayList<>()).add(n);
        }
        for (var entry : categories.entrySet()) {
            roots.add(WheelNode.category(
                    Component.translatable("wheel.zenkai." + entry.getKey()),
                    COL_CATEGORY, entry.getValue()));
        }
        return WheelNode.category(Component.empty(), ZenkaiPalette.TEXT, roots);
    }

    /**
     * Hijos de "Forms", agrupados en 3 niveles fijos en vez de por categoría/raza (pedido del
     * usuario, 2026-09-28 — antes se amontonaban por subcarpeta, ver captura de esa fecha):
     *  - Race: la cadena normal de la raza (SSJ1-4 del saiyan, second/third/final del
     *    arcosiano...) — {@link FormDef#descendable()} o sin ningún antepasado descendable.
     *  - Super Forms: lo que cuelga de una forma descendable sin serlo ella misma (hoy solo
     *    Golden/Black/sus variantes "fifth" del arcosiano, que cuelgan de final_form).
     *  - Divine: {@link FormDef#divineTier()} — los pares god/blue/rose de cada raza, cruzando
     *    razas dentro de la misma etiqueta (un jugador solo ve la suya, claro).
     * Ver {@link #tierOf}. 'active' en cada hoja = la forma SELECCIONADA, no la que lleva
     * puesta. Ni Ultra Instinct/Ultra Ego NI la ruta Mystic/Beast pasan por aquí — las 3 rutas de
     * endgame tienen su propia rama suelta, ver {@link #endgameBranch}.
     */
    private static List<WheelNode> forms(Player p) {
        PlayerStatsAttachment st = p.getData(ZenkaiDataAttachments.PLAYER_STATS.get());
        PlayerFormAttachment fm = p.getData(ZenkaiDataAttachments.PLAYER_FORM.get());

        Race race = st.getRace();
        ResourceLocation selected = fm.getSelectedForm();

        List<WheelNode> raceTier = new ArrayList<>();
        List<WheelNode> superTier = new ArrayList<>();
        List<WheelNode> divineTier = new ArrayList<>();

        // Recorre el ÁRBOL de verdad (FormRegistry.childrenOf, TODOS los hijos) en vez del
        // camino único de FormAccess.chain/FormRegistry.nextFrom (que solo sigue el primer
        // hijo): con hermanas equivalentes en la rama divina (ssj_blue/ssj_rose, mismo parent
        // ssj_god — ver DivineForms) el camino único se comería una de las dos en silencio, y
        // aquí SÍ hace falta ofrecer las dos para que el jugador elija cuál llevar puesta.
        Set<ResourceLocation> seen = new HashSet<>();
        Deque<ResourceLocation> queue = new ArrayDeque<>();
        ResourceLocation first = FormRegistry.firstFormFor(race);
        if (first != null) queue.add(first);
        // Ikari es raíz fuera de la cadena (como Oozaru) pero SÍ se elige en la rueda: solo aparece si se tiene.
        if (FormRegistry.get(FormIds.IKARI) != null && FormRegistry.isAllowed(race, FormIds.IKARI)) queue.add(FormIds.IKARI);
        while (!queue.isEmpty()) {
            ResourceLocation id = queue.poll();
            if (!seen.add(id)) continue; // corta ciclos de un datapack roto

            // Solo se enseña lo que ya se tiene. Sin ningún nodo de forma comprado, un saiyan ve
            // SSJ1 y nada más: SSJ2/3/4 no existen para él hasta comprarlos. Enseñarlos en
            // gris adelantaba la progresión y llenaba la rueda de opciones muertas.
            if (FormAccess.usable(p, id)) {
                addTieredLeaf(raceTier, superTier, divineTier, id, selected);
            }
            for (ResourceLocation child : FormRegistry.childrenOf(id)) {
                FormDef cd = FormDef.get(child);
                if (cd != null && cd.allows(race)) queue.add(child);
            }
        }

        List<WheelNode> out = new ArrayList<>();
        if (!raceTier.isEmpty()) out.add(WheelNode.category(
                Component.translatable("wheel.zenkai.forms_tier.race"), COL_FORMS, raceTier));
        if (!superTier.isEmpty()) out.add(WheelNode.category(
                Component.translatable("wheel.zenkai.forms_tier.super"), COL_FORMS, superTier));
        if (!divineTier.isEmpty()) out.add(WheelNode.category(
                Component.translatable("wheel.zenkai.forms_tier.divine"), COL_FORMS, divineTier));
        return out;
    }

    private static void addTieredLeaf(List<WheelNode> race, List<WheelNode> superForms,
                                      List<WheelNode> divine, ResourceLocation id, ResourceLocation selected) {
        // Oculta por el jugador (clic derecho en el árbol de transformaciones). La seleccionada se ve siempre.
        if (!id.equals(selected) && com.hmc.zenkai.config.ClientConfig.wheelFormHidden(id.toString())) return;
        FormDef def = FormDef.get(id);
        int color = def == null ? COL_FORMS : (0xFF000000 | def.auraRgb());
        WheelNode leaf = WheelNode.leaf(WheelNode.Kind.FORM, id.toString(),
                Component.translatable(FormDef.nameKey(id)), color,
                true, id.equals(selected));
        switch (tierOf(def)) {
            case DIVINE -> divine.add(leaf);
            case SUPER -> superForms.add(leaf);
            default -> race.add(leaf);
        }
    }

    private enum Tier { RACE, SUPER, DIVINE }

    /**
     * divineTier() manda siempre (energía divina, cruza razas). Si no, descendable() ==
     * "Race": es la cadena de la raza (o uno de sus limitadores). Si no es descendable pero
     * ALGÚN antepasado suyo sí lo es, es una continuación por encima de un limitador liberado
     * ("Super Forms" — hoy solo Golden/Black/sus variantes del arcosiano, que cuelgan de
     * final_form). Cualquier otro caso (cadenas sin ningún tramo descendable, como la del
     * saiyan) cae en "Race" por descarte: no tienen Super Forms hoy, y está bien que esa
     * sub-rama simplemente no aparezca para ellas.
     */
    private static Tier tierOf(FormDef def) {
        if (def == null) return Tier.RACE;
        if (def.divineTier()) return Tier.DIVINE;
        if (def.descendable()) return Tier.RACE;
        ResourceLocation parent = def.parent();
        while (parent != null) {
            FormDef parentDef = FormDef.get(parent);
            if (parentDef == null) return Tier.RACE;
            if (parentDef.descendable()) return Tier.SUPER;
            parent = parentDef.parent();
        }
        return Tier.RACE;
    }

    /**
     * Una ruta de endgame como rama propia: "Technique" (el interruptor on/off de SkillToggles
     * — mismo id que la forma solo por coincidencia de nombre, sin relación de código, ver
     * SkillToggles.java) + "Form" (la transformación de verdad, la cadena de EndgamePaths que
     * antes vivía enterrada dentro de "Forms"). {@code toggleId} null = esta ruta no tiene
     * técnica parcial (la ruta Mystic, a diferencia de Ultra Instinct/Ultra Ego): entonces las
     * hojas de la forma cuelgan sueltas de la rama en vez de envolverse en un sub-nodo "Form"
     * que no aportaría nada al ser el único hijo real. null si NI el interruptor (cuando lo
     * hay) NI ninguna forma de la cadena están disponibles todavía: no se enseña una rama vacía.
     */
    private static WheelNode endgameBranch(Player p, String toggleId, Component label, ResourceLocation formRoot) {
        List<WheelNode> kids = new ArrayList<>();

        if (toggleId != null) {
            WheelNode technique = techniqueToggleNode(p, toggleId);
            if (technique != null) kids.add(technique);
        }

        List<WheelNode> formLeaves = endgameChainLeaves(p, formRoot);
        if (!formLeaves.isEmpty()) {
            if (toggleId != null) {
                kids.add(WheelNode.category(
                        Component.translatable("wheel.zenkai.endgame_form"), COL_FORMS, formLeaves));
            } else {
                kids.addAll(formLeaves);
            }
        }

        if (kids.isEmpty()) return null;
        return WheelNode.category(label, COL_CATEGORY, kids);
    }

    /**
     * Carpeta "Potential Unlock": hoja "Mystic" (la FORMA potential_unlock, una forma propia que se
     * elige aquí y se carga con la tecla de transformar; ya no es un interruptor) + las formas de
     * la ruta que el jugador tenga (Beast, evolución de Mystic). null si no tiene ninguna.
     */
    private static WheelNode potentialUnlockBranch(Player p) {
        List<WheelNode> kids = new ArrayList<>();
        ResourceLocation mystic = FormIds.POTENTIAL_UNLOCK;
        if (FormDef.get(mystic) != null && FormAccess.unlocked(p, mystic)) {
            FormDef def = FormDef.get(mystic);
            ResourceLocation selected = p.getData(ZenkaiDataAttachments.PLAYER_FORM.get()).getSelectedForm();
            kids.add(WheelNode.leaf(WheelNode.Kind.FORM, mystic.toString(),
                    Component.translatable(FormDef.nameKey(mystic)),
                    0xFF000000 | def.auraRgb(), true, mystic.equals(selected)));
        }
        // Beast: evolución de Mystic, aparece cuando la furia lo desbloquea (EndgamePaths.unlocked).
        kids.addAll(endgameChainLeaves(p, FormIds.BEAST));
        if (kids.isEmpty()) return null;
        return WheelNode.category(Component.translatable("wheel.zenkai.potential_unlock_branch"),
                COL_CATEGORY, kids);
    }

    /** Misma pasada BFS que antes hacía forms() para las rutas de endgame, pero acotada a UNA
     *  sola raíz — reutilizada por endgameBranch() para Ultra Instinct y Ultra Ego. */
    private static List<WheelNode> endgameChainLeaves(Player p, ResourceLocation root) {
        ResourceLocation selected = p.getData(ZenkaiDataAttachments.PLAYER_FORM.get()).getSelectedForm();
        List<WheelNode> out = new ArrayList<>();
        Set<ResourceLocation> seen = new HashSet<>();
        Deque<ResourceLocation> queue = new ArrayDeque<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            ResourceLocation id = queue.poll();
            if (!seen.add(id)) continue;
            if (com.hmc.zenkai.feature.skills.EndgamePaths.unlocked(p, id)) {
                FormDef def = FormDef.get(id);
                int color = def == null ? COL_FORMS : (0xFF000000 | def.auraRgb());
                out.add(WheelNode.leaf(WheelNode.Kind.FORM, id.toString(),
                        Component.translatable(FormDef.nameKey(id)), color,
                        true, id.equals(selected)));
            }
            for (ResourceLocation child : FormRegistry.childrenOf(id)) {
                FormDef cd = FormDef.get(child);
                if (cd != null) queue.add(child);
            }
        }
        return out;
    }

    /**
     * Interruptor del kaioken: UNA hoja que alterna. La etiqueta lleva el escalón actual
     * porque el escalón se sube con la tecla de transformar, no aquí.
     * null si el jugador no tiene la habilidad: no se enseña lo que no existe.
     */
    private static WheelNode kaiokenToggle(Player p) {
        PlayerFormAttachment fm = p.getData(ZenkaiDataAttachments.PLAYER_FORM.get());
        if (SkillEffects.level(p, KAIOKEN_SKILL) <= 0) return null;

        boolean on = fm.isKaiokenSwitch();
        KaiokenTier tier = fm.getKaioken();

        Component label = on
                ? Component.translatable("wheel.zenkai.kaioken.on",
                tier.isOn() ? tier.label() : "—")
                : Component.translatable("wheel.zenkai.kaioken.off");

        return WheelNode.leaf(WheelNode.Kind.KAIOKEN, "", label,
                on ? COL_ON : COL_OFF, true, on);
    }

    /**
     * "Descender": vuelve a Base al instante desde una forma de CONTENCIÓN (second_form/
     * third_form/final_form del arcosiano hoy — FormDef.descendable()). Golden/Black no lo
     * llevan a propósito: esas se revierten con el tap de siempre, no con este atajo.
     * null si la forma actual no es descendable: no se enseña una acción sin efecto.
     */
    private static WheelNode descendNode(Player p) {
        PlayerFormAttachment fm = p.getData(ZenkaiDataAttachments.PLAYER_FORM.get());
        FormDef active = fm.activeDef();
        if (active == null || !active.descendable()) return null;
        return WheelNode.leaf(WheelNode.Kind.DESCEND, "",
                Component.translatable("wheel.zenkai.descend"), COL_DESCEND, true, false);
    }

    /**
     * Estilo de la cola de Saiyan ("loose"/"cintura"): capricho cosmético SIN costo, no una
     * habilidad — por eso no vive en SkillToggles.Toggle (que exige nivel de skill). Solo se
     * enseña con raza Saiyan Y cola activa (PlayerStatsAttachment.hasTail(), servicio de
     * Kami): no tiene sentido ofrecer un estilo de algo que no existe. Alterna entre los dos
     * únicos estilos; no hay un tercer estado "sin cola" aquí, eso lo decide Kami aparte.
     */
    private static WheelNode tailStyleToggle(Player p) {
        PlayerStatsAttachment st = p.getData(ZenkaiDataAttachments.PLAYER_STATS.get());
        if (st.getRace() != Race.SAIYAN || !st.hasTail()) return null;

        PlayerVisualAttachment vis = p.getData(ZenkaiDataAttachments.PLAYER_VISUAL.get());
        boolean waist = "waist".equals(vis.getTailStyleId());
        Component label = Component.translatable(
                waist ? "wheel.zenkai.tail_mode.waist" : "wheel.zenkai.tail_mode.loose");
        return WheelNode.leaf(WheelNode.Kind.TAIL_MODE, "", label,
                waist ? COL_ON : COL_OFF, true, waist);
    }

    private static WheelNode toggleNode(Player p, String id) {
        if (!SkillToggles.available(p, id)) return null;
        boolean on = SkillToggles.isOn(p, id);
        Component label = Component.translatable(
                on ? "wheel.zenkai.toggle.on" : "wheel.zenkai.toggle.off",
                Component.translatable("skill.zenkai." + id));
        return WheelNode.leaf(WheelNode.Kind.TOGGLE, id, label, on ? COL_ON : COL_OFF, true, on);
    }

    /** Igual que toggleNode(), pero para un interruptor que ya cuelga de una rama con su
     *  propio nombre (Ultra Instinct/Ultra Ego, ver endgameBranch): repetir el nombre de la
     *  habilidad en la etiqueta sería redundante ("Ultra Instinct > Ultra Instinct ACTIVADO"),
     *  así que esta etiqueta es genérica ("Technique ACTIVADA/DESACTIVADA"). */
    private static WheelNode techniqueToggleNode(Player p, String id) {
        if (!SkillToggles.available(p, id)) return null;
        // La técnica cuenta como ACTIVA también si la forma correspondiente está puesta (la forma
        // implica la técnica: ver OverlayMetersSystem.uiActive/ueActive), para no confundir.
        ResourceLocation form = p.getData(ZenkaiDataAttachments.PLAYER_FORM.get()).getFormId();
        boolean formImplies = (SkillEffects.ULTRA_INSTINCT.equals(id) && OverlayMetersSystem.isUiForm(form))
                || (SkillEffects.ULTRA_EGO.equals(id) && OverlayMetersSystem.isUeForm(form));
        boolean on = SkillToggles.isOn(p, id) || formImplies;
        Component label = Component.translatable(on ? "wheel.zenkai.technique.on" : "wheel.zenkai.technique.off");
        return WheelNode.leaf(WheelNode.Kind.TOGGLE, id, label, on ? COL_ON : COL_OFF, true, on);
    }
}