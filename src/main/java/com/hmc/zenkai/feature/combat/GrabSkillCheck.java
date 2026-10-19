package com.hmc.zenkai.feature.combat;

import java.util.Random;

/**
 * Matemática COMPARTIDA del skill check con el que un jugador agarrado intenta zafarse (cliente y servidor la
 * leen de aquí: si cada lado tuviera su fórmula, la barra que se ve y la que juzga el servidor se desviarían).
 *
 * La aguja recorre la barra de un extremo al otro y vuelve (onda triangular, 0 -> 1 -> 0) en 2 * needleTicks
 * ticks; esa es también la vida del check, y pasada sin pulsar cuenta como fallo. La zona de acierto cae en un
 * sitio distinto cada vez, derivado de una semilla del agarre y del número de check.
 */
public final class GrabSkillCheck {
    private GrabSkillCheck() {}

    /** Holgura (fracción de la barra) a cada lado de la zona: el cliente ve la aguja con el retraso de la red. */
    public static final double TOLERANCE = 0.03;
    /** Cuánto antes que el reloj del servidor puede decir el cliente que pulsó (ticks): cubre el ping. */
    public static final float LATENCY_TICKS = 8f;

    /** Posición de la aguja (0..1) tras {@code elapsed} ticks de check. Empieza en 0, sube hasta 1 y vuelve. */
    public static double needlePos(double elapsed, int needleTicks) {
        double phase = (elapsed / Math.max(1, needleTicks)) % 2.0;
        if (phase < 0) phase += 2.0;
        return phase < 1.0 ? phase : 2.0 - phase;
    }

    /** Inicio (0..1) de la zona de acierto del check {@code index}. Deja un margen a cada extremo de la barra. */
    public static double zoneStart(long seed, int index, double size) {
        double r = new Random(seed * 31L + index).nextDouble();
        double min = 0.2, max = Math.max(min, 0.95 - size);
        return min + r * (max - min);
    }

    public static boolean inZone(double pos, double zoneStart, double zoneSize) {
        return pos >= zoneStart - TOLERANCE && pos <= zoneStart + zoneSize + TOLERANCE;
    }
}
