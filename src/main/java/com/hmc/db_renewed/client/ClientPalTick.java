package com.hmc.db_renewed.client;

import com.hmc.db_renewed.client.input.KeyBindings;
import com.hmc.db_renewed.core.network.feature.player.PlayerFormAttachment;
import com.hmc.db_renewed.core.network.feature.stats.DataAttachments;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

public final class ClientPalTick {

    private static boolean lastHeld = false;
    private static int chainTicks = 0;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        KeyBindings.handleClientTick();

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        var form = mc.player.getData(DataAttachments.PLAYER_FORM.get());
        var stats = mc.player.getData(DataAttachments.PLAYER_STATS.get());

        boolean heldNow = form.isTransformHeld();

        boolean canTransform = PlayerFormAttachment.canTransformFrom(stats.getRace(), form.getFormId());

        if (!canTransform) {
            if (lastHeld) {
                lastHeld = false;
                chainTicks = 0;
                if (mc.player instanceof AbstractClientPlayer cp) {
                    DbPalAnimations.controller(cp).stopTriggeredAnimation();
                }
            }
            return;
        }

        // Desde aquí: solo si sí existe transformación configurada
        if (heldNow) {
            mc.player.input.forwardImpulse = 0;
            mc.player.input.leftImpulse = 0;
            mc.player.input.jumping = false;
            mc.player.input.shiftKeyDown = false;
            mc.player.setSprinting(false);
        }

        if (heldNow && !lastHeld) {
            lastHeld = true;
            if (mc.player instanceof AbstractClientPlayer cp) {
                DbPalAnimations.playTransformStart(cp);
                chainTicks = 10; // 0.5s
            }
        }

        if (!heldNow && lastHeld) {
            lastHeld = false;
            chainTicks = 0;
            if (mc.player instanceof AbstractClientPlayer cp) {
                DbPalAnimations.controller(cp).stopTriggeredAnimation();
            }
        }

        if (heldNow && chainTicks > 0) {
            chainTicks--;
            if (chainTicks == 0 && mc.player instanceof AbstractClientPlayer cp) {
                DbPalAnimations.playTransformLoop(cp);
            }
        }
    }
}
