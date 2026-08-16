package com.hmc.zenkai.feature.player;

import net.minecraft.server.level.ServerPlayer;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Ventana estrecha durante la cual el mixin de hardcore tiene permiso para actuar: desde que
 * el jugador muere hasta que termina de reaparecer.
procedimiento * POR QUÉ EXISTE. El mixin engancha ServerPlayer#setGameMode, que es un procedimiento público que
 * llama el mundo — incluido /gamemode. Sin esta ventana, un admin no podría poner en
 * espectador a nadie que estuviera en el Otro Mundo, y el mod estaría secuestrando un comando
 * de vanilla para siempre a cambio de arreglar un instante.
procedimiento * EN MEMORIA, SIN PERSISTIR, a propósito: si el servidor se cae entre la muerte y el respawn,
 * al volver el jugador ya no está reapareciendo. Un flag guardado en disco se quedaría
 * encendido y volveríamos al problema de arriba sin forma de apagarlo.
 */
public final class HardcoreRespawnWindow {
    private HardcoreRespawnWindow() {}

    private static final Set<UUID> RESPAWNING =
            Collections.newSetFromMap(new WeakHashMap<>());

    public static void open(ServerPlayer player) {
        RESPAWNING.add(player.getUUID());
    }

    public static void close(ServerPlayer player) {
        RESPAWNING.remove(player.getUUID());
    }

    public static boolean isOpen(ServerPlayer player) {
        return RESPAWNING.contains(player.getUUID());
    }
}