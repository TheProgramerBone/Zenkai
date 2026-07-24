package com.hmc.zenkai.client;

import java.util.Map;

/**
 * Estilos de aura seleccionables por el {@code aura_type} del FormDef (datapack).
 * Cada estilo define la GEOMETRÍA de la gota (faldones), el pulso, la velocidad de
 * animación y si lleva núcleo blanco. Las texturas (cuadrantes de aura_flame_N) se
 * comparten entre estilos. Tipos desconocidos o vacíos caen a "default".
 * El kaioken NO es un estilo: es capa de color (AuraColors.Layers) y la capa
 * envolvente hereda el estilo de la forma interior.
 */
public final class AuraStyles {
    private AuraStyles() {}

    /**
     * Un faldón de la gota: anillo de {@code count} planos-silueta.
     * {@code tex} = cuadrante de la hoja: 0 llama ancha, 1 llama alta, 2 penacho, 3 faldón bajo.
     * tilt &gt; 0 abre hacia fuera (base), tilt &lt; 0 cierra hacia dentro (cima de la gota).
     * yStart negativo hunde el faldón bajo los pies.
     */
    public record Skirt(int count, float offsetDeg, float tiltDeg, float baseR,
                        float width, float height, float yStart, float jitter,
                        float alpha, int tex) {}

    /**
     * @param skirts     geometría de la gota
     * @param scaleMul   multiplicador extra de tamaño del estilo
     * @param alphaMul   multiplicador extra de opacidad del estilo
     * @param pulseAmp   amplitud del pulso de respiración (0.05 = ±5%)
     * @param frameTicks ticks por frame de animación (menor = más frenética)
     * @param whiteCore  si dibuja el núcleo blanco interior (las auras oscuras no)
     */
    public record AuraStyle(Skirt[] skirts, float scaleMul, float alphaMul,
                            float pulseAmp, float frameTicks, boolean whiteCore) {}

    /** Gota estándar (la calibrada en sesión). */
    private static final AuraStyle DEFAULT = new AuraStyle(new Skirt[]{
            //        count offset  tilt   baseR  width  height yStart  jitter alpha tex
            new Skirt(8,    22.5f,  52f,   0.55f, 1.05f, 0.95f, -0.30f, 0.12f, 0.95f, 3),
            new Skirt(8,    0f,     26f,   0.45f, 1.10f, 1.90f, -0.20f, 0.18f, 1.00f, 0),
            new Skirt(8,    22.5f,  11f,   0.38f, 1.15f, 2.60f, -0.10f, 0.22f, 1.00f, 1),
            new Skirt(8,    0f,     -8f,   0.30f, 1.05f, 2.20f, 0.85f,  0.22f, 0.95f, 1),
            new Skirt(5,    36f,    -20f,  0.15f, 0.95f, 1.90f, 1.45f,  0.18f, 0.90f, 2),
    }, 1.0f, 1.0f, 0.05f, 1.5f, true);

    /** SSJ: más alta y puntiaguda, animación más frenética, pulso fuerte. */
    private static final AuraStyle SSJ = new AuraStyle(new Skirt[]{
            new Skirt(8,    22.5f,  50f,   0.55f, 1.00f, 1.05f, -0.30f, 0.15f, 0.95f, 3),
            new Skirt(8,    0f,     24f,   0.45f, 1.05f, 2.10f, -0.20f, 0.22f, 1.00f, 0),
            new Skirt(8,    22.5f,  9f,    0.38f, 1.10f, 2.95f, -0.10f, 0.28f, 1.00f, 1),
            new Skirt(8,    0f,     -10f,  0.30f, 1.00f, 2.50f, 0.95f,  0.28f, 0.95f, 1),
            new Skirt(5,    36f,    -22f,  0.15f, 0.90f, 2.20f, 1.60f,  0.22f, 0.90f, 2),
    }, 1.0f, 1.0f, 0.07f, 1.2f, true);

    /** Divine: suave y envolvente — masa ancha (q0), casi sin jitter, respiración lenta. */
    private static final AuraStyle DIVINE = new AuraStyle(new Skirt[]{
            new Skirt(8,    22.5f,  55f,   0.55f, 1.10f, 0.90f, -0.30f, 0.06f, 0.90f, 3),
            new Skirt(8,    0f,     27f,   0.45f, 1.15f, 1.80f, -0.20f, 0.10f, 0.95f, 0),
            new Skirt(8,    22.5f,  12f,   0.38f, 1.20f, 2.45f, -0.10f, 0.12f, 0.95f, 0),
            new Skirt(8,    0f,     -7f,   0.30f, 1.10f, 2.05f, 0.80f,  0.12f, 0.90f, 0),
            new Skirt(5,    36f,    -18f,  0.15f, 1.00f, 1.70f, 1.35f,  0.10f, 0.85f, 2),
    }, 1.0f, 0.92f, 0.03f, 2.2f, true);

    /** Dark: sin núcleo blanco, falda ancha, respiración pesada y lenta. */
    private static final AuraStyle DARK = new AuraStyle(new Skirt[]{
            new Skirt(8,    22.5f,  52f,   0.60f, 1.10f, 1.00f, -0.30f, 0.12f, 1.00f, 3),
            new Skirt(8,    0f,     26f,   0.45f, 1.10f, 1.90f, -0.20f, 0.18f, 1.00f, 0),
            new Skirt(8,    22.5f,  11f,   0.38f, 1.15f, 2.60f, -0.10f, 0.22f, 1.00f, 1),
            new Skirt(8,    0f,     -8f,   0.30f, 1.05f, 2.20f, 0.85f,  0.22f, 1.00f, 1),
            new Skirt(5,    36f,    -20f,  0.15f, 0.95f, 1.90f, 1.45f,  0.18f, 0.95f, 2),
    }, 1.0f, 1.0f, 0.04f, 1.8f, false);

    private static final Map<String, AuraStyle> STYLES = Map.of(
            "default", DEFAULT,
            "ssj", SSJ,
            "divine", DIVINE,
            "dark", DARK);

    /** Estilo por clave de datapack; desconocido/null → default. */
    public static AuraStyle of(String type) {
        if (type == null) return DEFAULT;
        return STYLES.getOrDefault(type, DEFAULT);
    }
}