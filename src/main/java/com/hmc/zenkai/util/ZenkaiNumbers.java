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

    /** Exacto con separadores de miles (p. ej. 12,000,000). */
    public static String exact(long v) {
        return String.format(Locale.ROOT, "%,d", v);
    }

    /** Multiplicador con 2 decimales (p. ej. "2.50"), sin el "x" delante — quien lo llame lo
     *  antepone. Antes vivía duplicado como método privado en StatsScreen; TrainingHubScreen
     *  también lo necesita para el panel TP Modifiers, así que se movió aquí. */
    public static String fmt2(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }
}