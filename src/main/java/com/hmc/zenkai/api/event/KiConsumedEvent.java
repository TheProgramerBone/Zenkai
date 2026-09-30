package com.hmc.zenkai.api.event;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

/**
 * Se postea en {@code KiFirePacket#execute}, justo antes de {@code att.addEnergy(-cost)}. Un
 * listener puede leer/mutar {@link #amount()} (por ejemplo, para aplicar un descuento de addon)
 * antes de que se cobre — el valor que queda tras postear el evento es el que de verdad se
 * gasta. Cancelable: cancelarlo deja el disparo gratis (no se cobra ki), sin abortar la técnica
 * en sí — para eso está {@link TechniqueFiredEvent}, que se postea antes que este.
 */
public final class KiConsumedEvent extends Event implements ICancellableEvent {
    private final ServerPlayer player;
    private int amount;

    public KiConsumedEvent(ServerPlayer player, int amount) {
        this.player = player;
        this.amount = amount;
    }

    public ServerPlayer player() { return player; }
    public int amount() { return amount; }
    public void setAmount(int amount) { this.amount = amount; }
}
