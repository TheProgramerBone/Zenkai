package com.hmc.zenkai.feature.race.layer;

/**
 * Color de una capa que SIGUE a otro (la piel o el pelo) con su propio tono.
 *
 * Una capa así se define en su JSON como "con el color de referencia {@code ref}, esta capa es
 * {@code tone}" (p. ej. la sombra EFB08B de la piel F9C9AE). Con cualquier otro color base se
 * aplica la MISMA proporción canal a canal: base · tone / ref. Así, eligiendo como piel justo
 * el color de referencia, cada capa da exactamente el tono de la textura por defecto (petición
 * del usuario, 2026-10-12: el primer tono de piel del humano tenía que verse igual que el botón
 * "Default", y un tinte gris uniforme no podía, porque las sombras del arte son más saturadas).
 * Función pura, con gametest.
 */
public final class LayerTone {
    private LayerTone() {}

    public static int apply(int baseRgb, int toneRgb, int refRgb) {
        return (channel(baseRgb, toneRgb, refRgb, 16) << 16)
                | (channel(baseRgb, toneRgb, refRgb, 8) << 8)
                | channel(baseRgb, toneRgb, refRgb, 0);
    }

    private static int channel(int base, int tone, int ref, int shift) {
        int b = (base >> shift) & 0xFF, t = (tone >> shift) & 0xFF, r = (ref >> shift) & 0xFF;
        if (r == 0) return t; // referencia negra en ese canal: no hay proporción, se usa el tono tal cual
        return Math.min(255, Math.round(b * (float) t / r));
    }
}
