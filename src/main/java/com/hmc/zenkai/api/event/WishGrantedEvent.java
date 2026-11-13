package com.hmc.zenkai.api.event;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;

/**
 * Se postea cuando Shenlong concede un deseo ({@code WishFinalizer.finalizeWish}), cuando el efecto
 * ya se aplicó y el servidor cierra la sesión del deseo. Informativo, no cancelable: no puede
 * deshacer lo concedido. {@code description} es el texto que se difunde a los jugadores cercanos
 * (qué pidió), no un id estable.
 */
public final class WishGrantedEvent extends Event {
    private final ServerPlayer player;
    private final Component description;

    public WishGrantedEvent(ServerPlayer player, Component description) {
        this.player = player;
        this.description = description;
    }

    public ServerPlayer player() { return player; }
    public Component description() { return description; }
}
