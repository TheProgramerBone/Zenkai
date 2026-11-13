package com.hmc.zenkai.api.event;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Se postea cuando un jugador con la ventana de parry abierta va a parar un golpe o un proyectil de ki
 * ({@code ParryServer.tryParry} / {@code tryParryProjectile}), justo ANTES de resolverlo. Cancelable: el parry NO
 * ocurre (la ventana sigue abierta y el golpe o el proyectil hace su efecto normal). Sirve para vetar parrys
 * concretos (un arma que no se puede parar, una zona sin parry) sin tocar el sistema. Los parrys de NPC
 * ({@code tryMobParry}) no lo postean.
 * {@code attacker} es null si lo que llega es un proyectil sin dueño vivo.
 */
public final class PlayerParryEvent extends Event implements ICancellableEvent {
    private final ServerPlayer defender;
    @Nullable private final LivingEntity attacker;
    private final boolean projectile;
    private final boolean perfect;

    public PlayerParryEvent(ServerPlayer defender, @Nullable LivingEntity attacker, boolean projectile, boolean perfect) {
        this.defender = defender;
        this.attacker = attacker;
        this.projectile = projectile;
        this.perfect = perfect;
    }

    public ServerPlayer defender() { return defender; }
    @Nullable public LivingEntity attacker() { return attacker; }
    /** true si lo que se para es un proyectil de ki; false si es un golpe cuerpo a cuerpo. */
    public boolean projectile() { return projectile; }
    /** true si cae en los primeros ticks de la ventana (parry perfecto: más contraataque y devuelve proyectiles). */
    public boolean perfect() { return perfect; }
}
