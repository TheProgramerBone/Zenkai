package com.hmc.zenkai.util;

import java.util.Locale;

/**
 * Formateo de números grandes para el HUD/scouter (vidas y PL pueden llegar a millones/miles de
 * millones). Compacto: 950, 1.2K, 12.3K, 4.5M, 1.2B, 3.4T...
 * Para la pantalla de stats (donde quieres el valor exacto) usa {@link #exact(long)}.
 *
 * CONVENCIÓN POR DEFECTO DEL MOD: cualquier número que se dibuje en una screen/GUI y que pueda
 * crecer sin techo real (TP, power level, melee/defensa/ki power — cualquiera derivado del
 * power level, que ya llega a los millones, ver el logro pl_1m) pasa por aquí, no por
 * String.valueOf/String.format a pelo. Por debajo de un umbral razonable (StatsScreen.
 * COMPACT_FROM = 20 000 es el que ya usan los popups de Stats) el número exacto sin más ya es
 * corto y legible — no hace falta compactarlo ni añadir tooltip. Por encima, compacto con
 * format(long) MÁS un tooltip con exact(long) para quien necesite el valor real (mismo reparto
 * que StatsScreen.bigVal/tp_spent ya usan) — nunca solo lo uno o solo lo otro.
 */
public final class ZenkaiNumbers {
    private ZenkaiNumbers() {}

    private static final String[] UNITS = {"K", "M", "B", "T", "Qa", "Qi"};

    /** Compacto con sufijo (1 decimal por debajo de 100 de cada unidad). */
    public static String format(long v) {
        if (v < 0) return "-" + format(-v);
        if (v < 1000) return Long.toString(v);

        int u = -1;
        double d = v;
        while (d >= 1000.0 && u < UNITS.length - 1) { d /= 1000.0; u++; }
        if (d >= 999.5 && u < UNITS.length - 1) { d /= 1000.0; u++; }

        String s = (d >= 100.0)
                ? String.format(Locale.ROOT, "%.0f", d)
                : String.format(Locale.ROOT, "%.1f", d);
        if (s.endsWith(".0")) s = s.substring(0, s.length() - 2);
        return s + UNITS[u];
    }

    /** Compacto para un número con decimales (un ritmo por segundo): 1 decimal por debajo de
     *  1000 ("12.5"), sufijo a partir de ahí ("3.4K"). */
    public static String format(double v) {
        if (Math.abs(v) < 1000.0) return String.format(Locale.ROOT, "%.1f", v);
        return format(Math.round(v));
    }

    /** Exacto con decimales y separadores de miles (p. ej. 12,345.67), para el tooltip. */
    public static String exact(double v) {
        return String.format(Locale.ROOT, "%,.2f", v);
    }

    /** Exacto con separadores de miles (p. ej. 12,000,000). */
    public static String exact(long v) {
        return String.format(Locale.ROOT, "%,d", v);
    }

    /** Umbral de la convención: por debajo el número se enseña exacto, desde aquí compacto (con tooltip del exacto). */
    public static final long COMPACT_FROM = 20_000;

    /** Número entero de una screen según la convención: exacto por debajo de {@link #COMPACT_FROM}, compacto desde ahí. */
    public static String screen(long v) {
        return Math.abs(v) < COMPACT_FROM ? Long.toString(v) : format(v);
    }

    /** Igual que {@link #screen(long)} para un valor con decimales (daño, absorción): un decimal solo si no es entero. */
    public static String screen(double v) {
        if (Math.abs(v) >= COMPACT_FROM) return format(Math.round(v));
        return String.format(Locale.ROOT, v == Math.floor(v) ? "%.0f" : "%.1f", v);
    }

    /** ¿Se enseñaría compactado? Quien lo use añade el tooltip con {@link #exact(long)}. */
    public static boolean isCompacted(double v) {
        return Math.abs(v) >= COMPACT_FROM;
    }

    /** Multiplicador con 2 decimales (p. ej. "2.50"), sin el "x" delante — quien lo llame lo
     *  antepone. Antes vivía duplicado como función privada en StatsScreen; TrainingHubScreen
     *  también lo necesita para el panel TP Modifiers, así que se movió aquí. */
    public static String fmt2(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }

    /**
     * Toneladas de una screen (pesas, gravedad, capacidad), sin el " t": 2 decimales por debajo de 1 000,
     * 1 decimal hasta {@link #COMPACT_FROM} y compacto desde ahí ("52.8K"). Con la cámara de gravedad a ×500
     * el equipo pasa de 50 000 t y "52815.56 / 236014.92 t" se montaba sobre la etiqueta del popup de Training
     * (captura del usuario, 2026-11-20). Si {@link #isCompacted} es cierto, quien lo dibuja pone el exacto
     * ({@link #exact(double)}) en un tooltip.
     */
    public static String tons(double v) {
        if (isCompacted(v)) return format(Math.round(v));
        return String.format(Locale.ROOT, Math.abs(v) < 1000 ? "%.2f" : "%.1f", v);
    }

    /** Igual que {@link #fmt2} pero con más decimales — para el tooltip "valor exacto" de un
     *  número que en pantalla ya se redondeó a 2 decimales (p. ej. el tonelaje del panel "TP
     *  Modifiers" de Training: la fila enseña 2 decimales, el tooltip da los 4 reales). */
    public static String fmt4(double v) {
        return String.format(Locale.ROOT, "%.4f", v);
    }
}