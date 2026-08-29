package com.hmc.zenkai.feature.alignment;

/**
 * Tres estados discretos derivados del alineamiento continuo (-100..+100). Corte único en ±20 —
 * el mismo que ya usaban {@code StatsScreen} y {@code ZenkaiPalette.alignmentColor} cada uno por
 * su cuenta. Vive en feature/alignment (core, no client) porque ahora también lo consumen
 * sistemas de servidor: la colisión de la nube del HFIL, el color del aura y la IA de miedo de
 * los aldeanos.
 */
public enum AlignmentTier {
    GOOD, NEUTRAL, EVIL;

    public static final int GOOD_THRESHOLD =  20;
    public static final int EVIL_THRESHOLD = -20;

    public static AlignmentTier of(int alignment) {
        if (alignment > GOOD_THRESHOLD) return GOOD;
        if (alignment < EVIL_THRESHOLD) return EVIL;
        return NEUTRAL;
    }
}
