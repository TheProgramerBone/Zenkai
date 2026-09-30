package com.hmc.zenkai.client.overlay;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.event.tick.ArcosianPowerSystem;
import com.hmc.zenkai.feature.Race;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/**
 * Barras de las mecánicas de transformación, apiladas sobre la hotbar (encima del hueco que usa
 * KiChargeGaugeOverlay): Calor de Ultra Instinct, Daño acumulado de Ultra Ego y Power Points del
 * Arcosiano. Solo se dibuja la que tiene algo que mostrar. Los tres valores ya viajan en
 * PlayerStateFlags (mismo sync de stats que el resto), no hace falta packet propio.
 *
 * Mismo esquema que las barras Body/Stamina/Ki del HUD (ClientZenkaiHooks.drawStatBar): un PNG
 * de contorno vacío + otro con el interior relleno, y el relleno proporcional se hace recortando
 * el ANCHO de origen del segundo. Texturas y iconos los escriben tools/gen_transform_meter_bars.py
 * y tools/gen_transform_meter_icons.py (fila v=140 de icons.png): las constantes de geometría de
 * aquí son == a las de esos scripts.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public final class TransformMetersOverlay {
    private TransformMetersOverlay() {}

    private static final ResourceLocation BARS_EMPTY =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/meter_bars_empty.png");
    private static final ResourceLocation BARS_FULL =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/meter_bars_full.png");
    private static final ResourceLocation ICONS =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/icons.png");

    // == gen_transform_meter_bars.py
    private static final int TEX_W = 128, TEX_H = 32;
    private static final int ROW_H = 10;
    private static final int CONTENT_W = 113;              // SHEAR + BODY_W + TIP
    private static final int ROW_V_UI = 0, ROW_V_UE = 11, ROW_V_PP = 22;

    // == gen_transform_meter_icons.py: fila v=140, celdas de 20px (0 ojo UI, 1 llama UE, 2 rayo PP)
    private static final int ICON_V = 140;
    private static final int ICON_CELL = 20;
    private static final int ICON_DRAW = 12;

    private static final int ROW_STEP = ROW_H + 3;

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        PlayerStatsAttachment att = PlayerStatsAttachment.get(mc.player);
        if (!att.isRaceChosen()) return;

        double heat = att.getUiHeat();
        double ue = att.getUeCharge();
        double pp = att.getArcosianPp();
        double ppMax = att.getRace() == Race.ARCOSIAN ? ArcosianPowerSystem.ppMax(mc.player) : 0.0;

        GuiGraphics g = e.getGuiGraphics();
        int x = (g.guiWidth() - CONTENT_W) / 2 + ICON_DRAW / 2;
        // Por encima del anillo de ki (radio 14 + hueco) y de la hotbar.
        int y = g.guiHeight() - 64;

        if (heat > 0.5) {
            // Sobrecalentándose (>80%): el relleno pulsa hacia rojo para avisar del colapso.
            boolean danger = heat > 80.0;
            float pulse = danger ? 0.6f + 0.4f * (float) Math.abs(Math.sin(mc.player.tickCount * 0.35)) : 1f;
            bar(g, x, y, ROW_V_UI, heat / 100.0, 0, 1f, pulse, pulse);
            y -= ROW_STEP;
        }
        if (ue > 0.5) {
            bar(g, x, y, ROW_V_UE, ue / 100.0, 1, 1f, 1f, 1f);
            y -= ROW_STEP;
        }
        if (ppMax > 0.0 && pp > 0.5) {
            bar(g, x, y, ROW_V_PP, pp / ppMax, 2, 1f, 1f, 1f);
        }
    }

    private static void bar(GuiGraphics g, int x, int y, int rowV, double frac, int icon,
                            float r, float gr, float b) {
        g.blit(ICONS, x - ICON_DRAW - 2, y + (ROW_H - ICON_DRAW) / 2, ICON_DRAW, ICON_DRAW,
                icon * ICON_CELL, ICON_V, ICON_CELL, ICON_CELL, 256, 256);
        g.blit(BARS_EMPTY, x, y, 0f, rowV, CONTENT_W, ROW_H, TEX_W, TEX_H);
        int w = (int) Math.round(CONTENT_W * Math.max(0.0, Math.min(1.0, frac)));
        if (w > 0) {
            g.setColor(r, gr, b, 1f);
            g.blit(BARS_FULL, x, y, 0f, rowV, w, ROW_H, TEX_W, TEX_H);
            g.setColor(1f, 1f, 1f, 1f);
        }
    }
}
