package com.hmc.zenkai.client.gui;

/**
 * Paleta ÚNICA del alineamiento (-100..+100): rojo (malvado) -> gris (neutral) -> azul (bondadoso).
 *
 * Vive aquí y no duplicada en cada pantalla porque el mismo dato debe verse igual en cualquier
 * partes: la barra de StatsScreen y el marcador del sentir el ki tienen que hablar el mismo
 * idioma, y si cada uno lleva sus constantes acaban divergiendo al primer retoque.
 */
public final class AlignmentPalette {
    private AlignmentPalette() {}

    public static final int EVIL    = 0xD62828;
    public static final int NEUTRAL = 0x9A9A9A;
    public static final int GOOD    = 0x2D6CDF;

    /** Color RGB (sin alfa) de una posición 0..1 del gradiente. */
    public static int gradient(float t) {
        t = Math.max(0f, Math.min(1f, t));
        return (t < 0.5f)
                ? lerpRgb(EVIL, NEUTRAL, t * 2f)
                : lerpRgb(NEUTRAL, GOOD, (t - 0.5f) * 2f);
    }

    /** Color ARGB opaco de un valor de alineamiento (-100..+100). */
    public static int forAlignment(int alignment) {
        return 0xFF000000 | gradient((alignment + 100) / 200f);
    }

    public static int lerpRgb(int a, int b, float t) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int r = (int) (ar + (br - ar) * t), gg = (int) (ag + (bg - ag) * t), bl = (int) (ab + (bb - ab) * t);
        return (r << 16) | (gg << 8) | bl;
    }

    // ── Variante VIVA (fuego) ────────────────────────────────────────────────
    // Mismos tres anclajes conceptuales, pero pensados para algo que ARDE. El neutral no
    // puede ser gris aquí: una llama sin alineamiento no es incolora, es blanca al rojo.
    // Vive en esta clase y no suelta en el renderer para que las dos lecturas del mismo dato
    // sigan siendo obviamente la misma escala si alguien retoca los anclajes.

    public static final int VIVID_EVIL    = 0xFF3A32;
    public static final int VIVID_NEUTRAL = 0xFFF0C4;
    public static final int VIVID_GOOD    = 0x4FB4FF;

    public static int vividGradient(float t) {
        t = Math.max(0f, Math.min(1f, t));
        return (t < 0.5f)
                ? lerpRgb(VIVID_EVIL, VIVID_NEUTRAL, t * 2f)
                : lerpRgb(VIVID_NEUTRAL, VIVID_GOOD, (t - 0.5f) * 2f);
    }

    /** ARGB opaco y saturado de un alineamiento (-100..+100), para llamas y siluetas. */
    public static int vividForAlignment(int alignment) {
        return 0xFF000000 | vividGradient((alignment + 100) / 200f);
    }
}