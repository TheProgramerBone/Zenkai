package com.hmc.db_renewed.client;

import com.hmc.db_renewed.core.network.feature.stats.DataAttachments;
import com.hmc.db_renewed.core.network.feature.stats.PlayerStatsAttachment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;


public final class ClientPalTick {

    private static boolean lastTransforming = false;
    private static int chainTicks = 0;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        PlayerStatsAttachment att = mc.player.getData(DataAttachments.PLAYER_STATS.get());
        boolean transformingNow = att.isTransforming();

        if (transformingNow) {
            mc.player.input.forwardImpulse = 0;
            mc.player.input.leftImpulse = 0;
            mc.player.input.jumping = false;
            mc.player.input.shiftKeyDown = false;
            mc.player.setSprinting(false);
        }

        // pressed
        if (transformingNow && !lastTransforming) {
            lastTransforming = true;
            if (mc.player instanceof AbstractClientPlayer cp) {
                DbPalAnimations.playTransformStart(cp);
                chainTicks = 10; // 0.5s
            }
        }

        // released
        if (!transformingNow && lastTransforming) {
            lastTransforming = false;
            chainTicks = 0;
            if (mc.player instanceof AbstractClientPlayer cp) {
                DbPalAnimations.controller(cp).stopTriggeredAnimation();
                // si tu versión no tiene stopTriggeredAnimation():
                // DbPalAnimations.controller(cp).stop();
            }
        }

        // chain 1 -> 2
        if (transformingNow && chainTicks > 0) {
            chainTicks--;
            if (chainTicks == 0 && mc.player instanceof AbstractClientPlayer cp) {
                DbPalAnimations.playTransformLoop(cp);
            }
        }
    }
}