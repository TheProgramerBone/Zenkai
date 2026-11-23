package com.hmc.zenkai.feature.armor;

import com.hmc.zenkai.config.ServerConfig;

/**
 * Coste de imbuir atributos en una pieza (decisión 2026-11-16, §3.4 de pendientes-0.7.0): SIN tope por pieza, pero el coste
 * por punto crece de forma LOGARÍTMICA pasado un umbral por atributo; nada exponencial.
 * <pre>coste del punto n (n = puntos que la pieza YA tiene en ese atributo) = base · (1 + ln(1 + exceso / escala))
 * exceso = max(0, n − umbral)</pre>
 * Por debajo del umbral cada punto vale exactamente {@code base}. Con los valores por defecto (base 1000 FE, umbral 100,
 * escala 100) el punto 200 de una pieza cuesta ≈1693 FE y el 10 000 ≈5 605 FE: crece, pero nunca se dispara.
 * {@code base} está en FE; la máquina de la Fase 5b aplica el mismo {@link #factor} al fluido de Ki (la sangre NO escala:
 * cada sangre sube su +1/+2/+5 fijo). La infusión de la 5c abarata multiplicando el resultado, no tocando la curva.
 */
public final class ImbueCost {
    private ImbueCost() {}

    /** Multiplicador del punto {@code n} (≥ 1). Puro: no lee la config, para poder probarlo con cualquier valor. */
    public static double factor(int n, int threshold, double scale) {
        int excess = Math.max(0, n - Math.max(0, threshold));
        if (excess == 0) return 1.0;
        return 1.0 + Math.log1p(excess / Math.max(1e-9, scale));
    }

    public static double factor(int n) {
        return factor(n, ServerConfig.imbueCostThreshold(), ServerConfig.imbueCostScale());
    }

    /** FE del punto {@code n} con la config del mundo. */
    public static double pointCost(int n) {
        return ServerConfig.imbueCostBase() * factor(n);
    }

    /**
     * FE de subir una pieza de {@code current} a {@code current + points} en un atributo: la suma de los costes de cada
     * punto, redondeada hacia arriba una sola vez al final (redondear punto a punto encarecía de más los bloques grandes).
     * Bucle y no fórmula cerrada: la suma de logaritmos no tiene una forma cerrada limpia, y {@code points} viene de una
     * sangre (+1, +2 o +5) o de una tanda pequeña, así que el coste del bucle es irrelevante.
     */
    public static long totalCost(int current, int points, double base, int threshold, double scale) {
        if (points <= 0) return 0L;
        double sum = 0.0;
        for (int i = 0; i < points; i++) sum += factor(current + i, threshold, scale);
        return (long) Math.ceil(sum * Math.max(0.0, base) - 1e-9);
    }

    public static long totalCost(int current, int points) {
        return totalCost(current, points, ServerConfig.imbueCostBase(),
                ServerConfig.imbueCostThreshold(), ServerConfig.imbueCostScale());
    }
}
