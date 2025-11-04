package com.hmc.db_renewed.network.stats;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

public class ClientHooks {
    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post e) {
        var mc = Minecraft.getInstance();
        if (mc.player == null) return;

        PlayerStatsAttachment att = mc.player.getData(DataAttachments.PLAYER_STATS.get());

        GuiGraphics g = e.getGuiGraphics();
        int x = 10, y = 10;

        int sCur = att.getStamina(), sMax = att.getStaminaMax();
        drawBar(g, x, y, 100, 8, sCur, sMax, 0xFF00FF00, "Stamina: " + sCur + "/" + sMax);

        int kCur = att.getEnergy(), kMax = att.getEnergyMax();
        drawBar(g, x, y + 12, 100, 8, kCur, kMax, 0xFF00AAFF, "Ki: " + kCur + "/" + kMax);

        Double flyMult = att.getTempStat("clientFlyMult");
        if (flyMult != null) {
            g.drawString(mc.font, Component.literal(String.format("Fly x%.2f", flyMult)), x, y + 24, 0xFFFFFF);
        }
    }

    private static void drawBar(GuiGraphics g, int x, int y, int w, int h, int cur, int max, int color, String label) {
        int fill = (int)(w * (max <= 0 ? 0 : (cur / (double)max)));
        g.fill(x, y, x + w, y + h, 0x88000000);
        g.fill(x, y, x + fill, y + h, color);
        g.drawString(net.minecraft.client.Minecraft.getInstance().font, label, x + w + 6, y, 0xFFFFFF);
    }
}