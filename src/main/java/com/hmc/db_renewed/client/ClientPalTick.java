package com.hmc.db_renewed.client;

import com.hmc.db_renewed.client.input.KeyBindings;
import com.hmc.db_renewed.core.network.feature.stats.DataAttachments;
import com.hmc.db_renewed.core.network.feature.player.PlayerStatsAttachment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;


public final class ClientPalTick {

    private static boolean lastHeld = false;
    private static int chainTicks = 0;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        // Esto debe seguir corriendo para actualizar el estado local + mandar packets
        KeyBindings.handleClientTick();

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        var form = mc.player.getData(DataAttachments.PLAYER_FORM.get());

        boolean heldNow = form.isTransformHeld();

        if (heldNow) {
            mc.player.input.forwardImpulse = 0;
            mc.player.input.leftImpulse = 0;
            mc.player.input.jumping = false;
            mc.player.input.shiftKeyDown = false;
            mc.player.setSprinting(false);
        }

        // pressed (arranca anim)
        if (heldNow && !lastHeld) {
            lastHeld = true;
            if (mc.player instanceof AbstractClientPlayer cp) {
                DbPalAnimations.playTransformStart(cp);
                chainTicks = 10; // 0.5s
            }
        }

        // released (corta anim) -> esto también pasa cuando el server fuerza held=false al completar
        if (!heldNow && lastHeld) {
            lastHeld = false;
            chainTicks = 0;
            if (mc.player instanceof AbstractClientPlayer cp) {
                DbPalAnimations.controller(cp).stopTriggeredAnimation();
                // si tu versión no tiene stopTriggeredAnimation():
                // DbPalAnimations.controller(cp).stop();
            }
        }

        // chain start -> loop
        if (heldNow && chainTicks > 0) {
            chainTicks--;
            if (chainTicks == 0 && mc.player instanceof AbstractClientPlayer cp) {
                DbPalAnimations.playTransformLoop(cp);
            }
        }
    }
}