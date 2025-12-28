package com.hmc.db_renewed.client;

import com.hmc.db_renewed.DragonBlockRenewed;
import com.hmc.db_renewed.core.network.feature.ki.MouseHooks;
import com.hmc.db_renewed.core.network.feature.stats.DataAttachments;
import com.hmc.db_renewed.core.network.feature.player.PlayerStatsAttachment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

public final class ClientHooks {

    private ClientHooks() {}

    // =========================
    // Texturas
    // =========================

    /** HUD base 256x128 */
    private static final ResourceLocation HUD_TEX =
            ResourceLocation.fromNamespaceAndPath(DragonBlockRenewed.MOD_ID, "textures/gui/hud.png");

    /** Atlas de iconos 270x270 (celdas 18x18) */
    private static final ResourceLocation ICONS_TEX =
            ResourceLocation.fromNamespaceAndPath(DragonBlockRenewed.MOD_ID, "textures/gui/icons.png");

    private static final int HUD_TEX_W = 256;
    private static final int HUD_TEX_H = 128;

    private static final int ICONS_TEX_W = 270;
    private static final int ICONS_TEX_H = 270;

    // =========================
    // Layout (ajusta a gusto)
    // =========================

    private static final int PANEL_W = 150;
    private static final int PANEL_H = 40;

    private static final int BAR_W = 120;
    private static final int BAR_H = 8;

    private static final int ICON_SIZE = 20;

    // Posición HUD
    private static final int PANEL_X = 10;
    private static final int PANEL_Y = 10;

    // =========================
    // UVs HUD (rellena según tu png 256x128)
    // =========================

    // Fondo del panel en hud.png
    private static final int U_PANEL = 0;
    private static final int V_PANEL = 0;

    // Barras “vacías” (mismo sprite, distinto fill)
    // NOTA: aquí debes poner los UV reales de tu hud.png
    private static final int U_BAR_EMPTY = 0;
    private static final int V_BAR_EMPTY = 40;

    private static final int U_BAR_BODY_FILL = 0;
    private static final int V_BAR_BODY_FILL = 48;

    private static final int U_BAR_STAMINA_FILL = 0;
    private static final int V_BAR_STAMINA_FILL = 56;

    private static final int U_BAR_KI_FILL = 0;
    private static final int V_BAR_KI_FILL = 64;

    // =========================
    // Iconos (atlas icons.png 270x270)
    // =========================
    // Tú ya tienes el atlas; aquí defines el grid por (col, row).
    private static final IconUV ICON_FLY = IconUV.grid(3, 0);
    private static final IconUV ICON_KI_CHARGE = IconUV.grid(2, 0);
    private static final IconUV ICON_KI_ATTACK = IconUV.grid(1, 0);

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // F1: ocultar HUD vanilla → también ocultar el tuyo
        if (mc.options.hideGui) return;

        PlayerStatsAttachment stats = mc.player.getData(DataAttachments.PLAYER_STATS.get());
        if (!stats.isRaceChosen()) return;

        GuiGraphics g = e.getGuiGraphics();

        final int x = PANEL_X;
        final int y = PANEL_Y;

        // ========================
        // 1) FONDO DEL PANEL (HUD)
        // ========================
        blitHud(g, x, y, U_PANEL, V_PANEL, PANEL_W, PANEL_H);

        // Layout interno
        int barX = x + 20;
        int barY = y + 5;

        // ========================
        // 2) BODY
        // ========================
        drawBar(g, barX, barY,
                stats.getBody(), stats.getBodyMax(),
                U_BAR_EMPTY, V_BAR_EMPTY,
                U_BAR_BODY_FILL, V_BAR_BODY_FILL);

        // ========================
        // 3) STAMINA
        // ========================
        barY += 10;
        drawBar(g, barX, barY,
                stats.getStamina(), stats.getStaminaMax(),
                U_BAR_EMPTY, V_BAR_EMPTY,
                U_BAR_STAMINA_FILL, V_BAR_STAMINA_FILL);

        // ========================
        // 4) KI/ENERGY
        // ========================
        barY += 10;
        drawBar(g, barX, barY,
                stats.getEnergy(), stats.getEnergyMax(),
                U_BAR_EMPTY, V_BAR_EMPTY,
                U_BAR_KI_FILL, V_BAR_KI_FILL);

        // ========================
        // 5) ICONOS (debajo del panel, en fila)
        // ========================
        int iconX = x + 2;
        int iconY = y + PANEL_H + 2; // <-- debajo del panel

        int iconPad = 2;

        if (stats.isFlyEnabled()) {
            blitIcon(g, iconX, iconY, ICON_FLY);
            iconX += ICON_SIZE + iconPad;
        }

        if (stats.isChargingKi()) {
            blitIcon(g, iconX, iconY, ICON_KI_CHARGE);
            iconX += ICON_SIZE + iconPad;
        }

        if (MouseHooks.wasChargingKiAttack) {
            blitIcon(g, iconX, iconY, ICON_KI_ATTACK);
            iconX += ICON_SIZE + iconPad;
        }

        // ========================
        // 6) TEXTO % KI BLAST
        // ========================
        if (MouseHooks.wasChargingKiAttack && mc.level != null) {
            int percent = computeKiAttackPercent(mc);

            g.drawString(
                    mc.font,
                    Component.literal("Ki " + percent + "%"),
                    barX,
                    y + PANEL_H + 4,
                    0xFFFFAA00
            );
        }
    }

    // =========================================================
    // Helpers
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

    /** blit contra la textura hud.png (256x128) */
    private static void blitHud(GuiGraphics g, int x, int y, int u, int v, int w, int h) {
        g.blit(HUD_TEX, x, y, u, v, w, h, HUD_TEX_W, HUD_TEX_H);
    }

    /** blit contra atlas icons.png (270x270) */
    private static void blitIcon(GuiGraphics g, int x, int y, IconUV icon) {
        g.blit(ICONS_TEX, x, y, icon.u(), icon.v(), ICON_SIZE, ICON_SIZE, ICONS_TEX_W, ICONS_TEX_H);
    }

    /**
     * Dibuja una barra: vacío completo + fill recortado.
     * Usa sprites de tamaño BAR_W x BAR_H dentro del hud.png.
     */
    private static void drawBar(
            GuiGraphics g,
            int x, int y,
            int cur, int max,
            int uEmpty, int vEmpty,
            int uFill, int vFill
    ) {
        if (max <= 0) return;

        float pct = cur / (float) max;
        pct = Math.max(0f, Math.min(1f, pct));
        int filled = (int) (BAR_W * pct);

        // vacío
        blitHud(g, x, y, uEmpty, vEmpty, BAR_W, BAR_H);

        // fill recortado
        if (filled > 0) {
            blitHud(g, x, y, uFill, vFill, filled, BAR_H);
        }
    }

    /**
     * UV helper para atlas de iconos 18x18.
     * Tu atlas es 270x270 => 15 columnas (270/18 = 15).
     */
    private record IconUV(int u, int v) {
        static IconUV grid(int col, int row) {
            return new IconUV(col * ICON_SIZE, row * ICON_SIZE);
        }
    }
}
