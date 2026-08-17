package com.hmc.zenkai.feature.technique;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MECÁNICA de la bola de carga: sonido y difusión a los que ven al jugador. Nada más.
 * La identidad de la carga —qué slot, desde qué tick— vive en ActionState desde el paso 3.
 * Aquí quedaba un record Charging(slot, startTick) con slot siempre -1 y un startTick que ya
 * no consultaba nadie: dos campos que habían dejado de significar algo y que invitaban a
 * volver a leerlos como si fueran verdad. Reducido a marca de presencia.
 * Quién enciende y apaga: SOLO ActionResolver.
 */
public final class KiChargeServer {
    private KiChargeServer() {}

    private static final Set<UUID> ACTIVE = ConcurrentHashMap.newKeySet();

    public static boolean isCharging(ServerPlayer sp) { return ACTIVE.contains(sp.getUUID()); }

    /** Arranca la bola. NO VALIDA: lo hizo ActionResolver. */
    public static void begin(ServerPlayer sp, KiTechnique tech) {
        if (!ACTIVE.add(sp.getUUID())) return;
        broadcast(sp, tech, true);

        // Una vez al empezar, no en bucle: un sonido sostenido necesitaría instancia
        // persistente en cliente, y aquí lo que importa es que todos lo oigan arrancar.
        SoundEvent snd = TechniqueAssets.soundOf(tech.chargeSound());
        if (snd != null) {
            sp.level().playSound(null, sp.getX(), sp.getEyeY(), sp.getZ(),
                    snd, SoundSource.PLAYERS, 1.0f, 1.0f);
        }
    }

    /** Apaga la bola. Idempotente: lo llaman el resolver, la muerte y la desconexión. */
    public static void end(ServerPlayer sp) {
        if (!ACTIVE.remove(sp.getUUID())) return;
        broadcastStop(sp);
    }

    private static void broadcast(ServerPlayer sp, KiTechnique tech, boolean charging) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(sp, new KiChargeStatePacket(
                sp.getId(), charging, tech.rgb(), tech.size(),
                tech.type().ordinal(), tech.position().ordinal()));
    }

    private static void broadcastStop(ServerPlayer sp) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(sp,
                new KiChargeStatePacket(sp.getId(), false, 0, 1, 0, 0));
    }
}