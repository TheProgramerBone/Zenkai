package com.hmc.zenkai.event;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.aura.AuraClientState;
import com.hmc.zenkai.feature.combat.InCombatState;
import com.hmc.zenkai.feature.forms.KaiokenTier;
import com.hmc.zenkai.feature.player.PlayerFormAttachment;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.feature.skills.SkillEffects;
import com.hmc.zenkai.registry.ZenkaiDataAttachments;
import com.hmc.zenkai.util.ZenkaiNumbers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

public final class ClientZenkaiHooks {

    private ClientZenkaiHooks() {}

    // =========================
    // Icons atlas
    // =========================
    public static final ResourceLocation ICONS_TEX =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/icons.png");

    private static final int C_BODY_KAIOKEN = 0xFFFF6633;  // quemando vida
    private static final int C_BODY_STRAIN  = 0xFF9966CC;  // fatiga, stats penalizadas

    private static final int ICONS_TEX_W = 256;
    private static final int ICONS_TEX_H = 256;

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

    // =========================
    // Íconos
    // =========================
    private static final IconUV ICON_DIVINE = IconUV.grid(5, 0);
    private static final IconUV ICON_FLY = IconUV.grid(3, 0);
    private static final IconUV ICON_POTENTIAL_UNLOCKED = IconUV.grid(6,3);
    private static final IconUV ICON_IMMORTAL = IconUV.grid(11, 0);
    private static final IconUV ICON_IN_COMBAT = IconUV.grid(0, 0);
    private static final IconUV ICON_KAIOKEN = IconUV.grid(2, 2);
    private static final IconUV ICON_KI_CHARGE = IconUV.grid(0, 2);
    private static final IconUV ICON_LEGENDARY = IconUV.grid(5, 2);
    private static final IconUV ICON_MAJIN = IconUV.grid(4, 0);
    private static final IconUV ICON_STRAIN = IconUV.grid(10, 1);
    private static final IconUV ICON_TRANSFORMING = IconUV.grid(4, 2);
    private static final IconUV ICON_TURBO = IconUV.grid(1, 2);

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // F1: ocultar HUD vanilla → también ocultar el tuyo
        if (mc.options.hideGui) return;

        PlayerFormAttachment form = mc.player.getData(ZenkaiDataAttachments.PLAYER_FORM.get());

        PlayerStatsAttachment stats = mc.player.getData(ZenkaiDataAttachments.PLAYER_STATS.get());
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

        long now = mc.player.level().getGameTime();
        KaiokenTier tier = form.getKaioken();
        boolean strained = form.isStrained(now);
        int bodyColor = tier.isOn() ? C_BODY_KAIOKEN : (strained ? C_BODY_STRAIN : C_BODY_FILL);

        // ========================
        // 1) BODY
        // ========================
        drawBarWithNumbers(
                g,
                barX, barY,
                BAR_W, BAR_H,
                stats.getBody(), stats.getBodyMax(),
                bodyColor,
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
        // TODOS LOS ICONOS EN UNA SOLA LÍNEA (debajo del panel)
        // ========================
        int iconX = PANEL_X;
        int iconY = PANEL_Y + panelH + 12;

        // --- Estados "especiales" (antes de acciones, misma altura) ---
        if (form.isTransforming()) {
            drawBadge(g, iconX, iconY, ICON_TRANSFORMING);
            iconX += BADGE_SIZE + BADGE_PAD;
        }

        if (stats.isDivine()) {
            drawBadge(g, iconX, iconY, ICON_DIVINE);
            iconX += BADGE_SIZE + BADGE_PAD;
        }

        if (mc.player.getData(ZenkaiDataAttachments.PLAYER_VISUAL.get()).isMajinControlled()) {
            drawBadge(g, iconX, iconY, ICON_MAJIN);
            iconX += BADGE_SIZE + BADGE_PAD;
        }

        if (AuraClientState.localTurbo) {
            drawBadge(g, iconX, iconY, ICON_TURBO);
            iconX += BADGE_SIZE + BADGE_PAD;
        }

        // Excluyentes por construcción: el strain nace en el instante en que el kaioken se
        // apaga por agotamiento, así que nunca coinciden y comparten hueco en la fila.
        if (tier.isOn()) {
            drawBadge(g, iconX, iconY, ICON_KAIOKEN);
            drawBadgeLabel(g, iconX, iconY, tier.label(), 0xFFFF8866);
            iconX += BADGE_SIZE + BADGE_PAD;
        } else if (strained) {
            drawBadge(g, iconX, iconY, ICON_STRAIN);
            drawBadgeLabel(g, iconX, iconY, Math.round(form.strainSecondsLeft(now)) + "s", C_BODY_STRAIN);
            iconX += BADGE_SIZE + BADGE_PAD;
        }

        if (stats.isImmortal()) {
            drawBadge(g, iconX, iconY, ICON_IMMORTAL);
            iconX += BADGE_SIZE + BADGE_PAD;
        }

        if (stats.isLegendary()) {
            drawBadge(g, iconX, iconY, ICON_LEGENDARY);
            iconX += BADGE_SIZE + BADGE_PAD;
        }

        if (InCombatState.isInCombat(mc.player)) {
            drawBadge(g, iconX, iconY, ICON_IN_COMBAT);
            drawBadgeLabel(g, iconX, iconY,
                    InCombatState.secondsLeft(mc.player) + "s", 0xFFFF8866);
            iconX += BADGE_SIZE + BADGE_PAD;
        }

        // --- Acciones (misma altura) ---
        if (stats.isFlyEnabled() && SkillEffects.canFly(mc.player)) {
            drawBadge(g, iconX, iconY, ICON_FLY);
            iconX += BADGE_SIZE + BADGE_PAD;
        }

        if (stats.isChargingKi()) {
            drawBadge(g, iconX, iconY, ICON_KI_CHARGE);
            iconX += BADGE_SIZE + BADGE_PAD;
        }

        g.drawString(mc.font, Component.literal(
                        stats.getPowerPercent() + "%  PL " + ZenkaiNumbers.format(stats.getApparentPowerLevel())),
                PANEL_X, PANEL_Y + panelH + 4, 0xFFFFE066);
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
        String txt = ZenkaiNumbers.format(cur) + "/" + ZenkaiNumbers.format(max);
        g.drawString(mc.font, Component.literal(txt), x + w + TEXT_PAD, y - 1, 0xFFFFFFFF);
    }

    private static void drawBadge(GuiGraphics g, int x, int y, IconUV icon) {
        g.blit(ICONS_TEX, x, y, icon.u(), icon.v(), ICON_DRAW, ICON_DRAW, ICONS_TEX_W, ICONS_TEX_H);
    }

    private record IconUV(int u, int v) {
        static IconUV grid(int col, int row) {
            return new IconUV(col * ICON_CELL, row * ICON_CELL);
        }
    }

    /** Etiqueta corta pegada al borde inferior derecho de un badge ("x20", "12s"). */
    private static void drawBadgeLabel(GuiGraphics g, int x, int y, String text, int color) {
        if (text == null || text.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        int w = mc.font.width(text);
        g.drawString(mc.font, text, x + BADGE_SIZE - w, y + BADGE_SIZE - 8, color, true);
    }
}