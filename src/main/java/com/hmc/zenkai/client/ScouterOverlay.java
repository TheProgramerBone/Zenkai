package com.hmc.zenkai.client;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.core.config.StatsConfig;
import com.hmc.zenkai.core.network.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.util.ZenkaiNumbers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/**
 * HUD del scouter (estilo DragonBlockC): con el overlay activo y el scouter puesto, muestra el
 * PL de lo que tengas en la mira, cerca del crosshair. Color por fuerza relativa a TU PL
 * (umbral sense_ki.similar_threshold): verde = más débil, amarillo = similar, rojo = más fuerte.
 * Sin objetivo: "PL ---".
 * Texto simple por ahora; el marco/textura estilo scouter es de la fase de pulido visual
 * (Juan: textura + posicionamiento cuando toque).
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public final class ScouterOverlay {
    private ScouterOverlay() {}

    // Offset respecto al centro de la pantalla (crosshair).
    private static final int OFF_X = 12;
    private static final int OFF_Y = -18;

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        if (!ScouterClientState.isOverlayOn() || !ScouterClientState.isScouterEquipped(mc)) return;

        GuiGraphics g = e.getGuiGraphics();
        int cx = g.guiWidth() / 2;
        int cy = g.guiHeight() / 2;

        String txt;
        int color;
        if (ScouterClientState.hasTarget()) {
            long pl = ScouterClientState.targetPowerLevel();
            txt = "PL " + ZenkaiNumbers.format(pl);
            color = relativeColor(mc, pl);
        } else {
            txt = "PL ---";
            // Sin objetivo: el texto toma el color del cristal (tinte vanilla del scouter puesto).
            color = 0xFF000000 | ScouterClientState.scouterTint(mc);
        }

        g.drawString(mc.font, Component.literal(txt), cx + OFF_X, cy + OFF_Y, color);
    }

    /** Verde = más débil que tú, amarillo = similar (umbral config), rojo = más fuerte. */
    private static int relativeColor(Minecraft mc, long targetPl) {
        assert mc.player != null;
        PlayerStatsAttachment att = PlayerStatsAttachment.get(mc.player);
        long myPl = att.isRaceChosen()
                ? att.getPowerLevel()
                : Math.round(mc.player.getMaxHealth());

        double t = StatsConfig.senseKiSimilarThreshold();
        long similarFloor = Math.round(myPl * t);

        if (targetPl > myPl)            return 0xFFFF5555; // más fuerte
        if (targetPl >= similarFloor)   return 0xFFFFE066; // similar
        return 0xFF7CFC7C;                                 // más débil
    }
}