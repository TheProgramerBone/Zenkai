package com.hmc.zenkai.api.event;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

/**
 * Se postea justo antes de que una técnica (ki o física) surta efecto — {@code KiFirePacket
 * #execute}, antes de gastar energía/invocar el proyectil, y {@code PhysicalCombatServer
 * #execute}, antes de gastar estamina. Cancelable: cancelarlo aborta la técnica entera (nada de
 * daño, nada de coste). {@link #techniqueId()} es el id en minúsculas del tipo
 * ({@code KiTechniqueType.id()} o {@code PhysicalTechnique.name().toLowerCase()}), no el enum en
 * sí — así un addon puede reaccionar por nombre sin depender del tipo Java concreto.
 */
public final class TechniqueFiredEvent extends Event implements ICancellableEvent {
    private final ServerPlayer player;
    private final String techniqueId;
    private final boolean physical;
    /** -1 si no aplica (las técnicas físicas no tienen tamaño). */
    private final int size;

    public TechniqueFiredEvent(ServerPlayer player, String techniqueId, boolean physical, int size) {
        this.player = player;
        this.techniqueId = techniqueId;
        this.physical = physical;
        this.size = size;
    }

    public ServerPlayer player() { return player; }
    public String techniqueId() { return techniqueId; }
    public boolean physical() { return physical; }
    public int size() { return size; }
}
