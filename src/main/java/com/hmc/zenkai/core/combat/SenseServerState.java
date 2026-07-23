package com.hmc.zenkai.core.combat;

import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Estado SERVIDOR del sentir el ki y del lock-on.
 *
 * "¿Tiene el sense encendido?" se deduce de los propios escaneos: el cliente manda un
 * SenseKiScanPacket periódicamente mientras el modo no sea OFF, así que basta con
 * recordar cuándo llegó el último. Evita un packet extra solo para el modo.
 *
 * El lock se guarda para poder avisar al fijado UNA vez (no en cada tick) y para tener
 * el dato disponible si en el futuro hace falta en servidor.
 */
public final class SenseServerState {
    private SenseServerState() {}

    /** Sin escaneo en este margen de ticks, se considera el sense apagado. */
    private static final long SENSE_TIMEOUT_TICKS = 40L;

    /** Tope duro de distancia aceptada para un lock (evita packets inventados). */
    public static final double MAX_LOCK_DISTANCE = 256.0;

    private static final Map<UUID, Long> LAST_SCAN = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> LOCKED = new ConcurrentHashMap<>();

    /** Llamar al recibir un SenseKiScanPacket. */
    public static void markScan(ServerPlayer sp) {
        LAST_SCAN.put(sp.getUUID(), sp.level().getGameTime());
    }

    public static boolean senseActive(ServerPlayer sp) {
        Long t = LAST_SCAN.get(sp.getUUID());
        return t != null && sp.level().getGameTime() - t <= SENSE_TIMEOUT_TICKS;
    }

    /** Guarda el lock. Devuelve true solo si el objetivo CAMBIÓ (para avisar una vez). */
    public static boolean setLock(ServerPlayer sp, int targetId) {
        Integer prev = LOCKED.put(sp.getUUID(), targetId);
        return prev == null || prev != targetId;
    }

    public static void clearLock(ServerPlayer sp) {
        LOCKED.remove(sp.getUUID());
    }

    /** Entidad fijada por este jugador, o -1. */
    public static int lockOf(ServerPlayer sp) {
        return LOCKED.getOrDefault(sp.getUUID(), -1);
    }

    /** Limpieza al desloguear. */
    public static void forget(UUID id) {
        LAST_SCAN.remove(id);
        LOCKED.remove(id);
    }
}