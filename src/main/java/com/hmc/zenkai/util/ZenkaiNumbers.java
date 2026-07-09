package com.hmc.zenkai.util;

import java.util.Locale;

/**
 * Formateo de números grandes para el HUD/scouter (vidas y PL pueden llegar a millones/miles de
 * millones). Compacto: 950, 1.2K, 12.3K, 4.5M, 1.2B, 3.4T...
 * Para la pantalla de stats (donde quieres el valor exacto) usa {@link #exact(long)}.
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
}