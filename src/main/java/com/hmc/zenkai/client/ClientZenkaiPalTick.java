package com.hmc.zenkai.client;

import com.hmc.zenkai.client.input.KeyBindings;
import com.hmc.zenkai.core.network.feature.player.PlayerFormAttachment;
import com.hmc.zenkai.core.network.feature.stats.DataAttachments;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ClientZenkaiPalTick {

    /** Estado de animación por jugador (antes era global -> por eso solo animaba al local). */
    private static final class AnimState {
        boolean lastHeld = false;
        int chainTicks = 0;
    }

    private static final Map<UUID, AnimState> STATES = new HashMap<>();

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        KeyBindings.handleClientTick();

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        // Recorremos TODOS los jugadores visibles para reproducir la animación de cada uno
        // según SU estado de transformación (que ya llega sincronizado por SyncPlayerFormPacket).
        for (AbstractClientPlayer p : mc.level.players()) {
            tickPlayer(mc, p);
        }

        // Limpieza: descartar estados de jugadores que ya no están en el nivel.
        STATES.keySet().removeIf(uuid -> mc.level.getPlayerByUUID(uuid) == null);
    }

    private static void tickPlayer(Minecraft mc, AbstractClientPlayer p) {
        var form  = p.getData(DataAttachments.PLAYER_FORM.get());
        var stats = p.getData(DataAttachments.PLAYER_STATS.get());

        AnimState st = STATES.computeIfAbsent(p.getUUID(), k -> new AnimState());

        boolean heldNow = form.isTransformHeld();
        boolean canTransform = PlayerFormAttachment.canTransformFrom(stats.getRace(), form.getFormId());

        if (!canTransform) {
            if (st.lastHeld) {
                st.lastHeld = false;
                st.chainTicks = 0;
                ZenkaiPalAnimations.controller(p).stopTriggeredAnimation();
            }
            return;
        }

        // El bloqueo de input SOLO aplica al jugador local (los demás no tienen input local aquí).
        if (heldNow && p == mc.player) {
            mc.player.input.forwardImpulse = 0;
            mc.player.input.leftImpulse = 0;
            mc.player.input.jumping = false;
            mc.player.input.shiftKeyDown = false;
            mc.player.setSprinting(false);
        }

        if (heldNow && !st.lastHeld) {
            st.lastHeld = true;
            ZenkaiPalAnimations.playTransformStart(p);
            st.chainTicks = 10; // 0.5s
        }

        if (!heldNow && st.lastHeld) {
            st.lastHeld = false;
            st.chainTicks = 0;
            ZenkaiPalAnimations.controller(p).stopTriggeredAnimation();
        }

        if (heldNow && st.chainTicks > 0) {
            st.chainTicks--;
            if (st.chainTicks == 0) {
                ZenkaiPalAnimations.playTransformLoop(p);
            }
        }
    }
}