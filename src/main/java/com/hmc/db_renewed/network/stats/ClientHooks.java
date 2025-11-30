package com.hmc.db_renewed.network.stats;

import com.hmc.db_renewed.network.ki.MouseHooks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

public class ClientHooks {

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // Si el jugador tiene F1 (hideGui) activado, NO dibujamos nuestro HUD
        if (mc.options.hideGui) return;

        PlayerStatsAttachment att = mc.player.getData(DataAttachments.PLAYER_STATS.get());

        // Si todavía no ha elegido raza, no mostramos nada del HUD del mod
        if (!att.isRaceChosen()) {
            return;
        }

        GuiGraphics g = e.getGuiGraphics();
        int x = 10, y = 10;

        // --- Barra de VIDA (usando salud vanilla) ---
        // --- Barra de VIDA del sistema del mod (body) ---
        int bCur = att.getBody();
        int bMax = att.getBodyMax();
        drawBar(g, x, y, 100, 8, bCur, bMax, 0xFFFF5555,
                "Body: " + bCur + "/" + bMax);

        // Luego movemos las demás barras hacia abajo
        int sCur = att.getStamina(), sMax = att.getStaminaMax();
        drawBar(g, x, y + 12, 100, 8, sCur, sMax, 0xFF00FF00,
                "Stamina: " + sCur + "/" + sMax);

        int kCur = att.getEnergy(), kMax = att.getEnergyMax();
        drawBar(g, x, y + 24, 100, 8, kCur, kMax, 0xFF00AAFF,
                "Ki: " + kCur + "/" + kMax);

        int textY = y + 36;

        // Multiplicador de vuelo (si lo tienes seteado)
        Double flyMult = att.getTempStat("clientFlyMult");
        if (flyMult != null) {
            g.drawString(mc.font,
                    Component.literal(String.format("Fly x%.2f", flyMult)),
                    x, textY, 0xFFFFFF);
            textY += 12;
        }

        // Estados varios
        String flyState = att.isFlyEnabled() ? "On" : "Off";
        g.drawString(mc.font, Component.literal("Fly: " + flyState), x, textY, 0xFFFFFF);
        textY += 12;

        String kiCharging = att.isChargingKi() ? "On" : "Off";
        g.drawString(mc.font, Component.literal("Charging Ki: " + kiCharging), x, textY, 0xFFFFFF);
        textY += 12;

        String chargingKiAttack = MouseHooks.wasChargingKiAttack ? "On" : "Off";
        g.drawString(mc.font, Component.literal("Charging KiAttack: " + chargingKiAttack), x, textY, 0xFFFFFF);
        textY += 12;

        // Porcentaje de carga del ataque de ki (0–200 %)
        if (MouseHooks.wasChargingKiAttack && mc.level != null) {
            long now = mc.level.getGameTime();
            long ticks = Math.max(0L, now - MouseHooks.clientChargeStartTick);
            long clamped = Math.min(
                    ticks,
                    com.hmc.db_renewed.network.ki.KiAttackServerLogic.MAX_TOTAL_CHARGE_TICKS
            );

            double factor;
            if (clamped <= com.hmc.db_renewed.network.ki.KiAttackServerLogic.MAX_BASE_CHARGE_TICKS) {
                factor = clamped /
                        (double) com.hmc.db_renewed.network.ki.KiAttackServerLogic.MAX_BASE_CHARGE_TICKS;
            } else {
                long over = clamped - com.hmc.db_renewed.network.ki.KiAttackServerLogic.MAX_BASE_CHARGE_TICKS;
                factor = 1.0 + over /
                        (double) com.hmc.db_renewed.network.ki.KiAttackServerLogic.MAX_OVER_CHARGE_TICKS;
            }

            int percent = (int) Math.round(factor * 100.0); // 0–200 %

            g.drawString(
                    mc.font,
                    Component.literal("Ki Attack: " + percent + "%"),
                    x,
                    textY,
                    0xFFFFAA00
            );
        }
    }

    private static void drawBar(GuiGraphics g, int x, int y, int w, int h,
                                int cur, int max, int color, String label) {
        int fill = (int) (w * (max <= 0 ? 0 : (cur / (double) max)));
        g.fill(x, y, x + w, y + h, 0x88000000);
        g.fill(x, y, x + fill, y + h, color);
        g.drawString(Minecraft.getInstance().font, label, x + w + 6, y, 0xFFFFFF);
    }
}