package com.hmc.zenkai.client;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.core.config.StatsConfig;
import com.hmc.zenkai.core.network.feature.sense.ScouterAreaDataPacket;
import com.hmc.zenkai.core.network.feature.race.RaceTextureUtil;
import com.hmc.zenkai.core.network.feature.stats.DataAttachments;
import com.hmc.zenkai.core.network.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.util.ZenkaiNumbers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/**
 * GUI del scouter (estilo DragonBlockC): lado IZQUIERDO, centrado verticalmente.
 * Tres líneas dentro del panel: título del modo (1x) + línea principal (2x) + subtítulo (1x).
 *
 * Modos ({@link ScouterMode}, F4 cicla):
 *  - PODER:      "PL 1.2M" + etiqueta DÉBIL (menos del 80% de tu PL) / FORMIDABLE (80-100%) /
 *                AMENAZA (te supera).
 *  - MÁS FUERTE: flecha de 8 direcciones (recalculada CADA FRAME con tu yaw, la posición viene
 *                del server cada 20 ticks) + PL y distancia. ▲/▼ si está claramente arriba/abajo.
 *  - RADAR:      flecha + distancia a la esfera más cercana; "MEJORA NO DISPONIBLE" sin la
 *                mejora de herrería.
 *
 * Marco de textura en 2 capas (mismo patrón que el ícono del ítem):
 *  - textures/gui/scouter/frame.png       -> base, sin teñir.
 *  - textures/gui/scouter/frame_tint.png  -> máscara en grises, teñida con el color del cristal.
 * Autodetección con {@link RaceTextureUtil#resourceExists}: sin frame.png se dibuja el panel
 * plano de respaldo. Fuente SIEMPRE derivada del tinte (aclarada, con piso de luminancia).
 * Juan ajusta: FRAME_W/FRAME_H (= tamaño del PNG), MARGIN_X y TEXT_OFF_X/Y.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public final class ScouterOverlay {
    private ScouterOverlay() {}

    /** Índice 0 = de frente, en sentido horario (yaw relativo / 45°). */
    private static final String[] ARROWS = {"\uD83E\uDC71", "\uD83E\uDC75", "\uD83E\uDC72", "\uD83E\uDC76", "\uD83E\uDC73", "\uD83E\uDC77", "\uD83E\uDC70", "\uD83E\uDC74"};
    // Referencia {"↑", "⬈", "→", "↘", "↓", "↙", "←", "↖"}

    /** Umbrales de la etiqueta del modo PODER (fracción de TU PL). */
    private static final double WEAK_BELOW = 0.8; // <80% débil; 80-100% formidable; >125% amenaza
    private static final double THREAT_ABOVE = 1.5;

    // --- Marco de textura (Juan ajusta a su PNG) ---
    private static final ResourceLocation FRAME_TEX =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/scouter/frame.png");
    private static final ResourceLocation FRAME_TINT_TEX =
            RaceTextureUtil.deriveMask(FRAME_TEX, "_tint");
    private static final int FRAME_W = 300;
    private static final int FRAME_H = 199;
    private static final int TEXT_OFF_X = 50;
    private static final int TEXT_OFF_Y = 70;

    // --- Comunes / panel plano de respaldo ---
    private static final int MARGIN_X = 0;
    private static final int TITLE_SCALE = 2; // título del modo
    private static final int MAIN_SCALE = 3;  // línea principal
    private static final int SUB_SCALE = 1;   // subtítulo (etiqueta/distancia)
    private static final int PAD_X = 6;
    private static final int PAD_Y = 5;
    private static final int LINE_GAP = 2;

    // Caché de existencia del marco (recheck periódico: recoge F3+T sin costo por frame).
    private static boolean frameExists = false;
    private static long nextCheckMs = 0L;

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        if (!ScouterClientState.isOverlayOn() || !ScouterClientState.isScouterEquipped(mc)) return;

        GuiGraphics g = e.getGuiGraphics();
        int tint = ScouterClientState.scouterTint(mc);
        int textColor = 0xFF000000 | readableTint(tint);

        // ---- Contenido según modo ----
        ScouterMode mode = ScouterClientState.mode();
        Component title = styled(Component.translatable(mode.titleKey()));
        Component main;
        Component sub;

        switch (mode) {
            case POWER -> {
                if (ScouterClientState.hasTarget()) {
                    long pl = ScouterClientState.targetPowerLevel();
                    main = styled(Component.literal("PL " + ZenkaiNumbers.format(pl)));
                    sub = styled(Component.translatable(powerLabelKey(mc, pl)));
                } else {
                    main = styled(Component.literal("PL ---"));
                    sub = null;
                }
            }
            case STRONGEST -> {
                if (ScouterClientState.areaStatus() == ScouterAreaDataPacket.STATUS_FOUND) {
                    main = styled(Component.literal(arrowTo(mc) + " PL "
                            + ZenkaiNumbers.format(ScouterClientState.areaPl())));
                    sub = styled(Component.translatable("scouter.zenkai.meters", distanceTo(mc)));
                } else {
                    main = styled(Component.literal("---"));
                    sub = styled(Component.translatable("scouter.zenkai.no_signal"));
                }
            }
            case RADAR -> {
                byte st = ScouterClientState.areaStatus();
                if (st == ScouterAreaDataPacket.STATUS_FOUND) {
                    main = styled(Component.literal(arrowTo(mc) + " " + distanceTo(mc) + "m"));
                    sub = styled(Component.translatable("scouter.zenkai.radar.ball"));
                } else if (st == ScouterAreaDataPacket.STATUS_UNAVAILABLE) {
                    main = styled(Component.literal("---"));
                    sub = styled(Component.translatable("scouter.zenkai.radar.unavailable"));
                } else {
                    main = styled(Component.literal("---"));
                    sub = styled(Component.translatable("scouter.zenkai.no_signal"));
                }
            }
            default -> { return; }
        }

        // ---- Marco o panel plano ----
        long now = System.currentTimeMillis();
        if (now >= nextCheckMs) {
            nextCheckMs = now + 2000;
            frameExists = RaceTextureUtil.resourceExists(FRAME_TEX);
        }

        if (frameExists) {
            drawFrame(g, mc, tint, textColor, title, main, sub);
        } else {
            drawFlatPanel(g, mc, tint, textColor, title, main, sub);
        }
    }

    // ------------------------------------------------------------------ contenido

    private static Component styled(Component c) {
        return c;
    }

    /** Etiqueta del modo PODER relativa a TU PL: débil / formidable / amenaza. */
    private static String powerLabelKey(Minecraft mc, long targetPl) {
        long myPl = ownPowerLevel(mc);
        if (targetPl >= Math.round(myPl * THREAT_ABOVE)) return "scouter.zenkai.label.threat";
        if (targetPl >= Math.round(myPl * WEAK_BELOW)) return "scouter.zenkai.label.formidable";
        return "scouter.zenkai.label.weak";
    }

    private static long ownPowerLevel(Minecraft mc) {
        assert mc.player != null;
        PlayerStatsAttachment att = mc.player.getData(DataAttachments.PLAYER_STATS.get());
        return att.isRaceChosen()
                ? att.getPowerLevel()
                : Math.round(mc.player.getMaxHealth() * StatsConfig.vanillaPowerLevelFactor());
    }

    /** Flecha de 8 direcciones hacia el objetivo cacheado, según TU yaw actual (per-frame). */
    private static String arrowTo(Minecraft mc) {
        assert mc.player != null;
        double dx = ScouterClientState.areaX() - mc.player.getX();
        double dz = ScouterClientState.areaZ() - mc.player.getZ();
        double dy = ScouterClientState.areaY() - mc.player.getEyeY();
        double horiz = Math.sqrt(dx * dx + dz * dz);

        // Muy arriba/abajo y casi encima: solo vertical.
        if (Math.abs(dy) > 6 && Math.abs(dy) > horiz) return dy > 0 ? "▲" : "▼";

        float targetYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float rel = Mth.wrapDegrees(targetYaw - mc.player.getYRot());
        int sector = Math.floorMod(Math.round(rel / 45f), 8);
        return ARROWS[sector];
    }

    private static long distanceTo(Minecraft mc) {
        assert mc.player != null;
        double dx = ScouterClientState.areaX() - mc.player.getX();
        double dy = ScouterClientState.areaY() - mc.player.getEyeY();
        double dz = ScouterClientState.areaZ() - mc.player.getZ();
        return Math.round(Math.sqrt(dx * dx + dy * dy + dz * dz));
    }

    // ------------------------------------------------------------------ dibujo

    private static void drawFrame(GuiGraphics g, Minecraft mc, int tint, int textColor,
                                  Component title, Component main, Component sub) {
        int x = MARGIN_X;
        int y = (g.guiHeight() - FRAME_H) / 2;
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
        g.blit(FRAME_TEX, x, y, 0, 0, FRAME_W, FRAME_H, FRAME_W, FRAME_H);

        float r = ((tint >> 16) & 0xFF) / 255f;
        float gr = ((tint >> 8) & 0xFF) / 255f;
        float b = (tint & 0xFF) / 255f;
        g.setColor(r, gr, b, 1f); // ⚠ GuiGraphics.setColor (existe en 1.21.1; se quitó en 1.21.2+)
        g.blit(FRAME_TINT_TEX, x, y, 0, 0, FRAME_W, FRAME_H, FRAME_W, FRAME_H);
        g.setColor(1f, 1f, 1f, 1f);

        drawLines(g, mc, x + TEXT_OFF_X, y + TEXT_OFF_Y, textColor, title, main, sub);
    }

    private static void drawFlatPanel(GuiGraphics g, Minecraft mc, int tint, int textColor,
                                      Component title, Component main, Component sub) {
        int lh = mc.font.lineHeight;
        int w = Math.max(mc.font.width(title) * TITLE_SCALE,
                Math.max(mc.font.width(main) * MAIN_SCALE,
                        sub == null ? 0 : mc.font.width(sub) * SUB_SCALE))
                + PAD_X * 2;
        int h = PAD_Y * 2 + lh * TITLE_SCALE + LINE_GAP + lh * MAIN_SCALE
                + (sub == null ? 0 : LINE_GAP + lh * SUB_SCALE);
        int x = MARGIN_X;
        int y = (g.guiHeight() - h) / 2;

        int border = 0xFF000000 | tint;
        g.fill(x, y, x + w, y + h, 0xA0000000);
        g.fill(x, y, x + w, y + h, 0x28000000 | tint);
        g.fill(x, y, x + w, y + 1, border);
        g.fill(x, y + h - 1, x + w, y + h, border);
        g.fill(x, y + 1, x + 1, y + h - 1, border);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, border);

        drawLines(g, mc, x + PAD_X, y + PAD_Y, textColor, title, main, sub);
    }

    /** Título + principal + subtítulo (opcional), apilados desde (x, y), cada uno a su escala. */
    private static void drawLines(GuiGraphics g, Minecraft mc, int x, int y, int color,
                                  Component title, Component main, Component sub) {
        int lh = mc.font.lineHeight;

        drawScaled(g, mc, title, x, y, TITLE_SCALE, color);
        y += lh * TITLE_SCALE + LINE_GAP;

        drawScaled(g, mc, main, x, y, MAIN_SCALE, color);
        y += lh * MAIN_SCALE + LINE_GAP;

        if (sub != null) {
            drawScaled(g, mc, sub, x, y, SUB_SCALE, color);
        }
    }

    private static void drawScaled(GuiGraphics g, Minecraft mc, Component c,
                                   int x, int y, int scale, int color) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1f);
        g.drawString(mc.font, c, 0, 0, color, true);
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