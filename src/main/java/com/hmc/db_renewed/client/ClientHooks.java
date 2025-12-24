package com.hmc.db_renewed.client;

import com.hmc.db_renewed.DragonBlockRenewed;
import com.hmc.db_renewed.core.network.feature.ki.MouseHooks;
import com.hmc.db_renewed.core.network.feature.stats.DataAttachments;
import com.hmc.db_renewed.core.network.feature.stats.PlayerStatsAttachment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

public class ClientHooks {

    // Textura del HUD (pon tu ruta real)
    private static final ResourceLocation HUD_TEX =
            ResourceLocation.fromNamespaceAndPath(DragonBlockRenewed.MOD_ID, "textures/gui/hud.png");

    // Constantes de tamaño (ajusta a tu sprite)
    private static final int PANEL_W = 150;
    private static final int PANEL_H = 40;

    private static final int BAR_W = 120;
    private static final int BAR_H = 8;

    private static final int ICON_SIZE = 16;

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // F1: ocultar HUD vanilla → también ocultar el tuyo
        if (mc.options.hideGui) return;

        PlayerStatsAttachment att = mc.player.getData(DataAttachments.PLAYER_STATS.get());

        // Si NO ha elegido raza, no mostramos nada de HUD del mod
        if (!att.isRaceChosen()) {
            return;
        }

        GuiGraphics g = e.getGuiGraphics();
        int x = 10;
        int y = 10;

        // ========================
        // 1) FONDO DEL PANEL
        // ========================
        // Supón que el fondo está en (0,0) de la textura
        g.blit(HUD_TEX, x, y, 0, 0, PANEL_W, PANEL_H);

        // Offset interno dentro del panel
        int barX = x + 20;     // dejar espacio a la izquierda para iconos o marco
        int barY = y + 5;

        // ========================
        // 2) BARRA BODY
        // ========================
        int bCur = att.getBody();
        int bMax = att.getBodyMax();
        drawHudBar(g, barX, barY, bCur, bMax,
                /*uEmpty*/0, 40,
                /*uFill*/0, 48);

        // ========================
        // 3) BARRA STAMINA
        // ========================
        barY += 10; // separación entre barras
        int sCur = att.getStamina();
        int sMax = att.getStaminaMax();
        drawHudBar(g, barX, barY, sCur, sMax,
                /*uEmpty*/0, 40,
                /*uFill*/0, 56);

        // ========================
        // 4) BARRA KI
        // ========================
        barY += 10;
        int kCur = att.getEnergy();
        int kMax = att.getEnergyMax();
        drawHudBar(g, barX, barY, kCur, kMax,
                /*uEmpty*/0, 40,
                /*uFill*/128, 40); // por ejemplo, otra franja de color para Ki

        // ========================
        // 5) ICONOS DE ESTADO
        // ========================
        int iconX = x + 2;
        int iconY = y + 2;

        // Icono de vuelo (ejemplo u=200,v=0)
        if (att.isFlyEnabled()) {
            g.blit(HUD_TEX, iconX, iconY, 200, 0, ICON_SIZE, ICON_SIZE);
            iconY += ICON_SIZE + 2;
        }

        // Icono cargando Ki (ejemplo u=216,v=0)
        if (att.isChargingKi()) {
            g.blit(HUD_TEX, iconX, iconY, 216, 0, ICON_SIZE, ICON_SIZE);
            iconY += ICON_SIZE + 2;
        }

        // Icono ataque de Ki cargándose (usar mismo icono o diferente)
        if (MouseHooks.wasChargingKiAttack) {
            g.blit(HUD_TEX, iconX, iconY, 232, 0, ICON_SIZE, ICON_SIZE);
            iconY += ICON_SIZE + 2;
        }

        // ========================
        // 6) % de carga del Ki Blast
        // ========================
        if (MouseHooks.wasChargingKiAttack && mc.level != null) {
            long now = mc.level.getGameTime();
            long ticks = Math.max(0L, now - MouseHooks.clientChargeStartTick);
            long clamped = Math.min(
                    ticks,
                    com.hmc.db_renewed.core.network.feature.ki.KiAttackServerLogic.MAX_TOTAL_CHARGE_TICKS
            );

            double factor;
            if (clamped <= com.hmc.db_renewed.core.network.feature.ki.KiAttackServerLogic.MAX_BASE_CHARGE_TICKS) {
                factor = clamped /
                        (double) com.hmc.db_renewed.core.network.feature.ki.KiAttackServerLogic.MAX_BASE_CHARGE_TICKS;
            } else {
                long over = clamped - com.hmc.db_renewed.core.network.feature.ki.KiAttackServerLogic.MAX_BASE_CHARGE_TICKS;
                factor = 1.0 + over /
                        (double) com.hmc.db_renewed.core.network.feature.ki.KiAttackServerLogic.MAX_OVER_CHARGE_TICKS;
            }

            int percent = (int) Math.round(factor * 100.0); // 0–200 %

            g.drawString(
                    mc.font,
                    Component.literal("Ki " + percent + "%"),
                    barX,
                    y + PANEL_H + 4,  // justo debajo del panel
                    0xFFFFAA00
            );
        }
    }

    /**
     * Dibuja una barra: primero el fondo vacío, luego la parte llena según cur/max.
     * Asume que empty y fill tienen el mismo tamaño (BAR_W x BAR_H).
     */
    private static void drawHudBar(
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

        // Barra vacía completa
        g.blit(HUD_TEX, x, y, uEmpty, vEmpty, BAR_W, BAR_H);

        // Parte llena recortada horizontalmente
        if (filled > 0) {
            g.blit(HUD_TEX, x, y, uFill, vFill, filled, BAR_H);
        }
    }
}