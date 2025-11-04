package com.hmc.db_renewed.network.stats;

import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

// Tick-like hook en cliente para ajustar flyingSpeed si aplica
public class FlyApplier {
    @SubscribeEvent
    public static void onClientView(ViewportEvent.ComputeCameraAngles e) {
        var mc = Minecraft.getInstance();
        if (mc.player == null) return;
        var att = mc.player.getData(DataAttachments.PLAYER_STATS.get());

        Double flyMult = att.getTempStat("clientFlyMult");
        if (flyMult != null && mc.player.getAbilities().mayfly) {
            mc.player.getAbilities().setFlyingSpeed((float)(0.02F * Math.min(2.0, flyMult)));
        }
    }
}