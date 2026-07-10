package com.hmc.zenkai.client;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.core.network.feature.race.RaceTextureUtil;
import com.hmc.zenkai.util.ZenkaiNumbers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/**
 * GUI del scouter (estilo DragonBlockC): lado IZQUIERDO, centrado verticalmente.
 *
 * Marco de textura en 2 capas (mismo patrón que el ícono del ítem):
 *  - textures/gui/scouter/frame.png       → base, sin teñir.
 *  - textures/gui/scouter/frame_tint.png  → máscara en grises, teñida con el color del cristal.
 * Auto-detección con {@link RaceTextureUtil#resourceExists}: si frame.png no existe todavía,
 * se dibuja el panel plano de respaldo (fondo oscuro + borde del tinte). Sin config.
 *
 * Fuente: SIEMPRE derivada del tinte del cristal (aclarada para legibilidad, con piso de
 * luminancia para tintes oscuros). Texto a 2x entero (píxeles nítidos, sin sombra).
 * Fuente pixelada custom: asignar SCOUTER_FONT cuando exista assets/zenkai/font/scouter.json.
 *
 * Juan ajusta: FRAME_W/FRAME_H (= tamaño del PNG), MARGIN_X y TEXT_OFF_X/Y (posición del
 * texto DENTRO del marco).
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public final class ScouterOverlay {
    private ScouterOverlay() {}

    /** Fuente pixelada estilo DBC. null = fuente vanilla hasta que exista la textura. */
    private static final ResourceLocation SCOUTER_FONT = null;
    // TODO Al tener la textura: ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "scouter");

    // --- Marco de textura (Juan ajusta tamaño/offsets a su PNG) ---
    private static final ResourceLocation FRAME_TEX =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/scouter/frame.png");
    private static final ResourceLocation FRAME_TINT_TEX =
            RaceTextureUtil.deriveMask(FRAME_TEX, "_tint");
    private static final int FRAME_W = 400;   // = ancho del PNG
    private static final int FRAME_H = 300;   // = alto del PNG
    private static final int TEXT_OFF_X = 50; // posición del texto dentro del marco
    private static final int TEXT_OFF_Y = 120;

    // --- Comunes / panel plano de respaldo ---
    private static final int MARGIN_X = 0;
    private static final int TEXT_SCALE = 2;
    private static final int PAD_X = 6;
    private static final int PAD_Y = 5;

    // Caché de existencia del marco (recheck periódico: recoge F3+T sin costo por frame).
    private static boolean frameExists = false;
    private static long nextCheckMs = 0L;

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        if (!ScouterClientState.isOverlayOn() || !ScouterClientState.isScouterEquipped(mc)) return;

        GuiGraphics g = e.getGuiGraphics();
        int tint = ScouterClientState.scouterTint(mc); // RGB del cristal

        String txt = ScouterClientState.hasTarget()
                ? "PL " + ZenkaiNumbers.format(ScouterClientState.targetPowerLevel())
                : "PL ---";
        int textColor = 0xFF000000 | readableTint(tint);

        Component label = Component.literal(txt)
                .withStyle(s -> SCOUTER_FONT == null ? s : s.withFont(SCOUTER_FONT));

        long now = System.currentTimeMillis();
        if (now >= nextCheckMs) {
            nextCheckMs = now + 2000;
            frameExists = RaceTextureUtil.resourceExists(FRAME_TEX);
        }

        if (frameExists) {
            drawFrame(g, mc, label, tint, textColor);
        } else {
            drawFlatPanel(g, mc, label, tint, textColor);
        }
    }

    /** Marco de 2 capas: base + máscara teñida. Texto en TEXT_OFF_X/Y dentro del marco. */
    private static void drawFrame(GuiGraphics g, Minecraft mc, Component label, int tint, int textColor) {
        int x = MARGIN_X;
        int y = (g.guiHeight() - FRAME_H) / 2;

        g.blit(FRAME_TEX, x, y, 0, 0, FRAME_W, FRAME_H, FRAME_W, FRAME_H);

        float r = ((tint >> 16) & 0xFF) / 255f;
        float gr = ((tint >> 8) & 0xFF) / 255f;
        float b = (tint & 0xFF) / 255f;
        g.setColor(r, gr, b, 1f); // ⚠ GuiGraphics.setColor (existe en 1.21.1; se quitó en 1.21.2+)
        g.blit(FRAME_TINT_TEX, x, y, 0, 0, FRAME_W, FRAME_H, FRAME_W, FRAME_H);
        g.setColor(1f, 1f, 1f, 1f);

        drawLabel(g, mc, label, x + TEXT_OFF_X, y + TEXT_OFF_Y, textColor);
    }

    /** Respaldo mientras no exista frame.png: panel oscuro + velo y borde del tinte. */
    private static void drawFlatPanel(GuiGraphics g, Minecraft mc, Component label, int tint, int textColor) {
        int tw = mc.font.width(label) * TEXT_SCALE;
        int th = mc.font.lineHeight * TEXT_SCALE;
        int w = tw + PAD_X * 2;
        int h = th + PAD_Y * 2;
        int x = MARGIN_X;
        int y = (g.guiHeight() - h) / 2;

        int border = 0xFF000000 | tint;
        g.fill(x, y, x + w, y + h, 0xA0000000);
        g.fill(x, y, x + w, y + h, 0x28000000 | tint);
        g.fill(x, y, x + w, y + 1, border);
        g.fill(x, y + h - 1, x + w, y + h, border);
        g.fill(x, y + 1, x + 1, y + h - 1, border);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, border);

        drawLabel(g, mc, label, x + PAD_X, y + PAD_Y, textColor);
    }

    private static void drawLabel(GuiGraphics g, Minecraft mc, Component label, int x, int y, int color) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(TEXT_SCALE, TEXT_SCALE, 1f);
        g.drawString(mc.font, label, 0, 0, color, false);
        g.pose().popPose();
    }

    /**
     * Aclara el tinte para que la fuente sea legible sobre el panel/marco oscuro:
     * mezcla 40% hacia blanco y, si sigue muy oscuro (tintes negro/gris), sube al piso.
     */
    private static int readableTint(int rgb) {
        int r = lift((rgb >> 16) & 0xFF);
        int g = lift((rgb >> 8) & 0xFF);
        int b = lift(rgb & 0xFF);
        // Luminancia aproximada; piso para tintes muy oscuros.
        int luma = (r * 3 + g * 6 + b) / 10;
        if (luma < 110) {
            int boost = 110 - luma;
            r = Math.min(255, r + boost);
            g = Math.min(255, g + boost);
            b = Math.min(255, b + boost);
        }
        return (r << 16) | (g << 8) | b;
    }

    private static int lift(int c) {
        return c + (255 - c) * 2 / 5; // +40% hacia blanco
    }
}