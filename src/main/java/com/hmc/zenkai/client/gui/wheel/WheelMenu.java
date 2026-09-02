package com.hmc.zenkai.client.gui.wheel;

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
import com.hmc.zenkai.feature.skills.SuperForms;
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
    private static final int COL_BASE     = ZenkaiPalette.TEXT_DIM;
    private static final int COL_ON       = ZenkaiPalette.OK;      // interruptor encendido
    private static final int COL_OFF      = ZenkaiPalette.DENIED;  // interruptor apagado
    private static final int COL_CATEGORY = ZenkaiPalette.MAXED;   // submenús de interruptores
    private static final int COL_DESCEND  = ZenkaiPalette.DENIED;  // "Descender": acción, no interruptor

    public static WheelNode build(Player p) {
        List<WheelNode> roots = new ArrayList<>();

        List<WheelNode> forms = forms(p);
        if (forms.size() > 1) { // 1 = solo Base: no hay nada que elegir
            roots.add(WheelNode.category(
                    Component.translatable("wheel.zenkai.forms"), COL_FORMS, forms));
        }

        WheelNode kaioken = kaiokenToggle(p);
        if (kaioken != null) roots.add(kaioken);

        WheelNode descend = descendNode(p);
        if (descend != null) roots.add(descend);

        WheelNode tailMode = tailStyleToggle(p);
        if (tailMode != null) roots.add(tailMode);
        // Interruptores: los sueltos cuelgan de la raíz, los que declaran categoría se
        // agrupan en su propia rama. Agrupar es SOLO presentación: el packet manda el mismo
        // kind TOGGLE con el mismo id, esté donde esté la hoja.
        Map<String, List<WheelNode>> categories = new LinkedHashMap<>();
        for (SkillToggles.Toggle t : SkillToggles.all()) {
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

    /** Cadena de formas. 'active' = la que está SELECCIONADA, no la que lleva puesta. */
    private static List<WheelNode> forms(Player p) {
        List<WheelNode> out = new ArrayList<>();
        PlayerStatsAttachment st = p.getData(ZenkaiDataAttachments.PLAYER_STATS.get());
        PlayerFormAttachment fm = p.getData(ZenkaiDataAttachments.PLAYER_FORM.get());

        Race race = st.getRace();
        ResourceLocation selected = fm.getSelectedForm();

        out.add(WheelNode.leaf(WheelNode.Kind.FORM, FormIds.BASE.toString(),
                Component.translatable("wheel.zenkai.base"), COL_BASE,
                true, FormIds.BASE.equals(selected)));

        // Recorre el ÁRBOL de verdad (FormRegistry.childrenOf, TODOS los hijos) en vez del
        // camino único de SuperForms.chain/FormRegistry.nextFrom (que solo sigue el primer
        // hijo): con hermanas equivalentes en la rama divina (ssj_blue/ssj_rose, mismo parent
        // ssj_god — ver DivineForms) el camino único se comería una de las dos en silencio, y
        // aquí SÍ hace falta ofrecer las dos para que el jugador elija cuál llevar puesta.
        Set<ResourceLocation> seen = new HashSet<>();
        Deque<ResourceLocation> queue = new ArrayDeque<>();
        ResourceLocation first = FormRegistry.firstFormFor(race);
        if (first != null) queue.add(first);
        while (!queue.isEmpty()) {
            ResourceLocation id = queue.poll();
            if (!seen.add(id)) continue; // corta ciclos de un datapack roto

            // Solo se enseña lo que ya se tiene. Con super_forms a nivel 1, un saiyan ve
            // SSJ1 y nada más: SSJ2/3/4 no existen para él hasta comprarlos. Enseñarlos en
            // gris adelantaba la progresión y llenaba la rueda de opciones muertas.
            if (SuperForms.unlocked(p, id)) {
                FormDef def = FormDef.get(id);
                int color = def == null ? COL_FORMS : (0xFF000000 | def.auraRgb());
                out.add(WheelNode.leaf(WheelNode.Kind.FORM, id.toString(),
                        Component.translatable("form.zenkai." + id.getPath()), color,
                        true, id.equals(selected)));
            }
            for (ResourceLocation child : FormRegistry.childrenOf(id)) {
                FormDef cd = FormDef.get(child);
                if (cd != null && cd.allows(race)) queue.add(child);
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
}