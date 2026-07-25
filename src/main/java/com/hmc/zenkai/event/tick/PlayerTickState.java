package com.hmc.zenkai.event.tick;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Estado TRANSITORIO por jugador del tick loop (solo servidor, no se persiste ni sincroniza).
 * Vive aquí y no en cada sistema para que exista UN solo punto de limpieza en el logout:
 * antes chargeTicks se quedaba colgado.
 */
public final class PlayerTickState {
    private PlayerTickState() {}

    /** Ticks seguidos cargando ki, por jugador. */
    private static final Map<UUID, Integer> CHARGE_TICKS = new HashMap<>();

    /**
     * Restos fraccionarios de regen por jugador [body, stamina, ki]. Sin esto, un 1%/s
     * sobre un pool pequeño se redondeaba a 0 y la config no servía de nada.
     * IMPORTANTE — el slot [0] es COMPARTIDO A PROPÓSITO entre el drenaje de kaioken
     * (KaiokenSystem, cada tick, resta) y la regen de body (RegenSystem, 1/s, suma).
     * Es la mecánica: con buena constitución la regeneración compensa la quema del
     * kaioken y el jugador lo aguanta. NO separar en dos acumuladores sin cambiar el diseño.
     */
    private static final Map<UUID, double[]> REGEN_CARRY = new HashMap<>();

    /** Acumulador fraccionario del jugador. [0] body (compartido con kaioken), [1] st, [2] ki. */
    public static double[] carry(UUID id) {
        return REGEN_CARRY.computeIfAbsent(id, k -> new double[3]);
    }

    /** Suma un tick de carga y devuelve el total acumulado. */
    public static int bumpCharge(UUID id) {
        return CHARGE_TICKS.merge(id, 1, Integer::sum);
    }

    public static void resetCharge(UUID id) {
        CHARGE_TICKS.remove(id);
    }

    /** Limpieza al desloguear. */
    public static void forget(UUID id) {
        CHARGE_TICKS.remove(id);
        REGEN_CARRY.remove(id);
    }
}