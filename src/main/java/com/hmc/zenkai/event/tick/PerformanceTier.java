package com.hmc.zenkai.event.tick;

/**
 * Escalón de rendimiento compartido por vuelo y movimiento en tierra.
 * Vive aparte porque es la ÚNICA regla que ambos sistemas comparten: duplicarla
 * sería garantizar que se desincronicen al retocar el balance.
 */
public final class PerformanceTier {
    private PerformanceTier() {}

    /**
     * Fracción del bonus máximo que recibe el jugador según el escalón.
     * El % de poder se aplica aparte (multiplicando), así que 0% siempre da vanilla.
     */
    public static double of(boolean control, boolean turbo) {
        if (!control) return 0.45;      // suelto: rápido pero maniobrable
        return turbo ? 1.0 : 0.7;       // el turbo exige Control pulsado
    }
}