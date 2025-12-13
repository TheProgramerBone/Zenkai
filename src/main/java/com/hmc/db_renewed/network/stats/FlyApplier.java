package com.hmc.db_renewed.network.stats;

import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

public class FlyApplier {
    @SubscribeEvent
    public static void onClientView(ViewportEvent.ComputeCameraAngles e) {
        var mc = Minecraft.getInstance();
        if (mc.player == null) return;

        if (mc.player.isCreative() || mc.player.isSpectator()) {
            return;
        }

        var att = mc.player.getData(DataAttachments.PLAYER_STATS.get());

        if (!att.isFlyEnabled()) {
            return;
        }

        double flyMult = att.getFlyMultiplier();
        if (mc.player.getAbilities().mayfly) {
            mc.player.getAbilities().setFlyingSpeed((float)(0.02F * flyMult));
        }
    }
}