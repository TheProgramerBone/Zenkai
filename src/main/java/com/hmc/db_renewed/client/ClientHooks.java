package com.hmc.db_renewed.client;

import com.hmc.db_renewed.DragonBlockRenewed;
import com.hmc.db_renewed.core.network.feature.Race;
import com.hmc.db_renewed.core.network.feature.ki.MouseHooks;
import com.hmc.db_renewed.core.network.feature.player.PlayerStatsAttachment;
import com.hmc.db_renewed.core.network.feature.stats.DataAttachments;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

import java.lang.reflect.Method;

public final class ClientHooks {

    private ClientHooks() {}

    // =========================
    // Icons atlas
    // =========================
    private static final ResourceLocation ICONS_TEX =
            ResourceLocation.fromNamespaceAndPath(DragonBlockRenewed.MOD_ID, "textures/gui/icons.png");

    private static final int ICONS_TEX_W = 270;
    private static final int ICONS_TEX_H = 270;

    // OJO: esto está como lo tienes actualmente
    private static final int ICON_CELL = 20;     // tamaño real de celda en el atlas
    private static final int ICON_DRAW = 20;     // tamaño al dibujar el icono
    private static final int BADGE_SIZE = 20;    // cuadrito contenedor
    private static final int BADGE_PAD = 2;

    // =========================
    // Layout HUD
    // =========================
    private static final int PANEL_X = 10;
    private static final int PANEL_Y = 10;

    private static final int BAR_W = 120;
    private static final int BAR_H = 8;
    private static final int BAR_GAP = 10;

    private static final int TEXT_PAD = 6;

    // Colores (ARGB)
    private static final int C_PANEL_BG   = 0xAA000000;
    private static final int C_PANEL_EDGE = 0x55FFFFFF;

    private static final int C_BAR_BG     = 0x66000000;
    private static final int C_BAR_EDGE   = 0x55FFFFFF;

    private static final int C_BODY_FILL  = 0xFFCC3333;
    private static final int C_STAM_FILL  = 0xFF33CC66;
    private static final int C_KI_FILL    = 0xFF33A0FF;

    // Badges (cuadritos)
    private static final int C_BADGE_BG   = 0xAA000000;
    private static final int C_BADGE_EDGE = 0x55FFFFFF;

    // =========================
    // Íconos existentes
    // =========================
    private static final IconUV ICON_FLY = IconUV.grid(3, 0);
    private static final IconUV ICON_KI_CHARGE = IconUV.grid(2, 0);
    private static final IconUV ICON_KI_ATTACK = IconUV.grid(1, 0);

    private static final IconUV ICON_TRANSFORMING = IconUV.grid(7, 0);
    private static final IconUV ICON_DIVINE = IconUV.grid(4, 0);
    private static final IconUV ICON_MAJIN = IconUV.grid(5, 0);
    private static final IconUV ICON_IMMORTAL = IconUV.grid(3, 0);
    private static final IconUV ICON_LEGENDARY = IconUV.grid(9, 0);

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // F1: ocultar HUD vanilla → también ocultar el tuyo
        if (mc.options.hideGui) return;

        PlayerStatsAttachment stats = mc.player.getData(DataAttachments.PLAYER_STATS.get());
        if (!stats.isRaceChosen()) return;

        GuiGraphics g = e.getGuiGraphics();

        // ========================
        // PANEL simple (sin textura)
        // ========================
        int panelW = 10 + BAR_W + 70; // barra + texto cur/max
        int panelH = (BAR_H * 3) + (BAR_GAP * 2); // padding + gaps

        drawPanel(g, PANEL_X, PANEL_Y, panelW, panelH);

        // Layout interno
        int barX = PANEL_X + 25;
        int barY = PANEL_Y + 8;

        // ========================
        // 1) BODY
        // ========================
        drawBarWithNumbers(
                g,
                barX, barY,
                BAR_W, BAR_H,
                stats.getBody(), stats.getBodyMax(),
                C_BODY_FILL,
                "HP"
        );

        // ========================
        // 2) STAMINA
        // ========================
        barY += BAR_GAP;
        drawBarWithNumbers(
                g,
                barX, barY,
                BAR_W, BAR_H,
                stats.getStamina(), stats.getStaminaMax(),
                C_STAM_FILL,
                "STM"
        );

        // ========================
        // 3) KI
        // ========================
        barY += BAR_GAP;
        drawBarWithNumbers(
                g,
                barX, barY,
                BAR_W, BAR_H,
                stats.getEnergy(), stats.getEnergyMax(),
                C_KI_FILL,
                "KI"
        );

        // ========================
        // TODOS LOS ICONOS EN UNA SOLA LINEA (debajo del panel)
        // ========================
        int iconX = PANEL_X;
        int iconY = PANEL_Y + panelH + 4;

        // --- Estados “especiales” (antes de acciones, misma altura) ---
        if (safeBool(stats, "isTransforming")) {
            drawBadge(g, iconX, iconY, ICON_TRANSFORMING);
            iconX += BADGE_SIZE + BADGE_PAD;
        }

        if (safeBool(stats, "isDivine")) {
            drawBadge(g, iconX, iconY, ICON_DIVINE);
            iconX += BADGE_SIZE + BADGE_PAD;
        }

        if (stats.getRace() == Race.MAJIN) {
            drawBadge(g, iconX, iconY, ICON_MAJIN);
            iconX += BADGE_SIZE + BADGE_PAD;
        }

        if (safeBool(stats, "isImmortal")) {
            drawBadge(g, iconX, iconY, ICON_IMMORTAL);
            iconX += BADGE_SIZE + BADGE_PAD;
        }

        if (safeBool(stats, "isLegendary")) {
            drawBadge(g, iconX, iconY, ICON_LEGENDARY);
            iconX += BADGE_SIZE + BADGE_PAD;
        }

        // --- Acciones (misma altura) ---
        if (stats.isFlyEnabled()) {
            drawBadge(g, iconX, iconY, ICON_FLY);
            iconX += BADGE_SIZE + BADGE_PAD;
        }

        if (stats.isChargingKi()) {
            drawBadge(g, iconX, iconY, ICON_KI_CHARGE);
            iconX += BADGE_SIZE + BADGE_PAD;
        }

        if (MouseHooks.wasChargingKiAttack) {
            drawBadge(g, iconX, iconY, ICON_KI_ATTACK);
            iconX += BADGE_SIZE + BADGE_PAD;
        }

        // Texto % KI attack
        if (MouseHooks.wasChargingKiAttack && mc.level != null) {
            int percent = computeKiAttackPercent(mc);
            g.drawString(
                    mc.font,
                    Component.literal("Ki " + percent + "%"),
                    iconX + 4,
                    iconY + 6,
                    0xFFFFAA00
            );
        }
    }

    // =========================================================
    // Helpers (Barras / Panel / Badges)
    // =========================================================

    private static void drawPanel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, C_PANEL_BG);
        g.fill(x, y, x + w, y + 1, C_PANEL_EDGE);
        g.fill(x, y + h - 1, x + w, y + h, C_PANEL_EDGE);
        g.fill(x, y, x + 1, y + h, C_PANEL_EDGE);
        g.fill(x + w - 1, y, x + w, y + h, C_PANEL_EDGE);
    }

    private static void drawBarWithNumbers(
            GuiGraphics g,
            int x, int y,
            int w, int h,
            int cur, int max,
            int fillColor,
            String label
    ) {
        Minecraft mc = Minecraft.getInstance();
        g.drawString(mc.font, Component.literal(label), x-20, y, 0xFFFFFFFF);

        // bar bg
        g.fill(x, y, x + w, y + h, C_BAR_BG);

        // fill
        if (max > 0) {
            float pct = Math.max(0f, Math.min(1f, cur / (float) max));
            int filled = (int) (w * pct);
            if (filled > 0) {
                g.fill(x, y, x + filled, y + h, fillColor);
            }
        }

        // borde
        g.fill(x, y, x + w, y + 1, C_BAR_EDGE);
        g.fill(x, y + h - 1, x + w, y + h, C_BAR_EDGE);
        g.fill(x, y, x + 1, y + h, C_BAR_EDGE);
        g.fill(x + w - 1, y, x + w, y + h, C_BAR_EDGE);

        // texto cur/max
        String txt = cur + "/" + max;
        g.drawString(mc.font, Component.literal(txt), x + w + TEXT_PAD, y - 1, 0xFFFFFFFF);
    }

    private static void drawBadge(GuiGraphics g, int x, int y, IconUV icon) {
        // cuadrito
        g.fill(x, y, x + BADGE_SIZE, y + BADGE_SIZE, C_BADGE_BG);
        g.fill(x, y, x + BADGE_SIZE, y + 1, C_BADGE_EDGE);
        g.fill(x, y + BADGE_SIZE - 1, x + BADGE_SIZE, y + BADGE_SIZE, C_BADGE_EDGE);
        g.fill(x, y, x + 1, y + BADGE_SIZE, C_BADGE_EDGE);
        g.fill(x + BADGE_SIZE - 1, y, x + BADGE_SIZE, y + BADGE_SIZE, C_BADGE_EDGE);

        // icon centrado
        int ix = x + (BADGE_SIZE - ICON_DRAW) / 2;
        int iy = y + (BADGE_SIZE - ICON_DRAW) / 2;

        g.blit(ICONS_TEX, ix, iy, icon.u(), icon.v(), ICON_DRAW, ICON_DRAW, ICONS_TEX_W, ICONS_TEX_H);
    }

    // =========================================================
    // Helpers (Ki attack %)
    // =========================================================
    private static int computeKiAttackPercent(Minecraft mc) {
        long now = mc.level.getGameTime();
        long ticks = Math.max(0L, now - MouseHooks.clientChargeStartTick);

        long clamped = Math.min(
                ticks,
                com.hmc.db_renewed.core.network.feature.ki.KiAttackServerLogic.MAX_TOTAL_CHARGE_TICKS
        );

        double factor;
        if (clamped <= com.hmc.db_renewed.core.network.feature.ki.KiAttackServerLogic.MAX_BASE_CHARGE_TICKS) {
            factor = clamped / (double) com.hmc.db_renewed.core.network.feature.ki.KiAttackServerLogic.MAX_BASE_CHARGE_TICKS;
        } else {
            long over = clamped - com.hmc.db_renewed.core.network.feature.ki.KiAttackServerLogic.MAX_BASE_CHARGE_TICKS;
            factor = 1.0 + over / (double) com.hmc.db_renewed.core.network.feature.ki.KiAttackServerLogic.MAX_OVER_CHARGE_TICKS;
        }

        return (int) Math.round(factor * 100.0); // 0–200%
    }

    private static boolean safeBool(Object obj, String methodName) {
        if (obj == null) return false;
        try {
            Method m = obj.getClass().getMethod(methodName);
            Object r = m.invoke(obj);
            return (r instanceof Boolean b) && b;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private record IconUV(int u, int v) {
        static IconUV grid(int col, int row) {
            return new IconUV(col * ICON_CELL, row * ICON_CELL);
        }
    }
}
