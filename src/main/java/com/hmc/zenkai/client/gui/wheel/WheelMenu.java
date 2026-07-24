package com.hmc.zenkai.client.gui.wheel;

import com.hmc.zenkai.core.network.feature.Race;
import com.hmc.zenkai.core.network.feature.forms.FormDef;
import com.hmc.zenkai.core.network.feature.forms.FormIds;
import com.hmc.zenkai.core.network.feature.forms.KaiokenTier;
import com.hmc.zenkai.core.network.feature.player.PlayerFormAttachment;
import com.hmc.zenkai.core.network.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.core.network.feature.stats.DataAttachments;
import com.hmc.zenkai.core.skills.SkillEffects;
import com.hmc.zenkai.core.skills.SuperForms;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;

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

    private static final int COL_FORMS = 0xFF7CFC7C;
    private static final int COL_BASE  = 0xFFBBBBBB;
    private static final int COL_ON    = 0xFF3FD24A; // interruptor encendido
    private static final int COL_OFF   = 0xFFE02020; // interruptor apagado

    public static WheelNode build(Player p) {
        List<WheelNode> roots = new ArrayList<>();

        List<WheelNode> forms = forms(p);
        if (forms.size() > 1) { // 1 = solo Base: no hay nada que elegir
            roots.add(WheelNode.category(
                    Component.translatable("wheel.zenkai.forms"), COL_FORMS, forms));
        }

        WheelNode kaioken = kaiokenToggle(p);
        if (kaioken != null) roots.add(kaioken);

        return WheelNode.category(Component.empty(), 0xFFFFFFFF, roots);
    }

    /** Cadena de formas. 'active' = la que está SELECCIONADA, no la que lleva puesta. */
    private static List<WheelNode> forms(Player p) {
        List<WheelNode> out = new ArrayList<>();
        PlayerStatsAttachment st = p.getData(DataAttachments.PLAYER_STATS.get());
        PlayerFormAttachment fm = p.getData(DataAttachments.PLAYER_FORM.get());

        Race race = st.getRace();
        ResourceLocation selected = fm.getSelectedForm();

        out.add(WheelNode.leaf(WheelNode.Kind.FORM, FormIds.BASE.toString(),
                Component.translatable("wheel.zenkai.base"), COL_BASE,
                true, FormIds.BASE.equals(selected)));

        for (ResourceLocation id : SuperForms.chain(race)) {
            FormDef def = FormDef.get(id);
            int color = def == null ? COL_FORMS : (0xFF000000 | def.auraRgb());
            out.add(WheelNode.leaf(WheelNode.Kind.FORM, id.toString(),
                    Component.translatable("form.zenkai." + id.getPath()), color,
                    SuperForms.unlocked(p, id), id.equals(selected)));
        }
        return out;
    }

    /**
     * Interruptor del kaioken: UNA hoja que alterna. La etiqueta lleva el escalón actual
     * porque el escalón se sube con la tecla de transformar, no aquí.
     * null si el jugador no tiene la habilidad: no se enseña lo que no existe.
     */
    private static WheelNode kaiokenToggle(Player p) {
        PlayerFormAttachment fm = p.getData(DataAttachments.PLAYER_FORM.get());
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
}