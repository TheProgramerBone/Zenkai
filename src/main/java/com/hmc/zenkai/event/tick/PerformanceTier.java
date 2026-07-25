package com.hmc.zenkai.event.tick;

/**
 * Escalón de rendimiento compartido por vuelo y movimiento en tierra.
 *
 * ANTES tenía tres escalones (0.45 suelto / 0.7 control / 1.0 turbo) y el TURBO era el
 * tercero del MISMO bonus. Eso hacía que su efecto dependiera del build y encima lo
 * diluyera powerFraction: en la práctica no se notaba al correr ni al volar.
 * Ahora esto solo mide el CONTROL, y el turbo es un multiplicador aparte y constante
 * (ver TURBO_SPEED_MULT en GroundMovementSystem / FlightSystem).
 */
public final class PerformanceTier {
    private PerformanceTier() {}

    /**
     * Fracción del bonus máximo según se esté pulsando Control.
     * Suelto rinde la mitad: rápido pero maniobrable.
     */
    public static double of(boolean control) {
        return control ? 1.0 : 0.5;
    }
}