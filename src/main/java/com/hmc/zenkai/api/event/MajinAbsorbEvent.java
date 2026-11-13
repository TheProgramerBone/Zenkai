package com.hmc.zenkai.api.event;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

/**
 * Se postea cuando la masa de un Majin alcanza a un objetivo que SÍ puede absorber (pasó las reglas de poder y vida de
 * {@code MajinAbsorption.refusal}), justo antes de matarlo y de sumar la masa. Cancelable: la absorción no ocurre
 * (el objetivo sobrevive y no se gasta masa; la masa se pierde en el impacto). Sirve para proteger criaturas o
 * jugadores concretos, o para exigir condiciones extra. La raza Majin está vetada en la 0.6.0 y se estrena en la 0.7.0.
 */
public final class MajinAbsorbEvent extends Event implements ICancellableEvent {
    private final ServerPlayer owner;
    private final LivingEntity target;

    public MajinAbsorbEvent(ServerPlayer owner, LivingEntity target) {
        this.owner = owner;
        this.target = target;
    }

    public ServerPlayer owner() { return owner; }
    public LivingEntity target() { return target; }
}
