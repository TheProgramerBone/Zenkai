package com.hmc.zenkai.feature.race.face;

import com.hmc.zenkai.feature.Race;

/**
 * Rasgos de la cara que dependen de la RAZA y no del rasgo elegido. Tabla en código (como
 * FaceRegions): son hechos del diseño de cada raza, no contenido que un addon deba cambiar.
 *
 *  · Cejas: Namekianos, Arcosianos y Majin NO tienen cejas (indicación del usuario, 2026-10-01).
 *    En esas razas no se pinta ninguna capa marcada como ceja ("eyebrow": true en el JSON del
 *    ojo) ni la categoría EYEBROWS, que además queda sin opciones elegibles.
 *  · Sombra de boca/nariz: las capas con color "skin" multiplican la piel por su gris (~0.78 en la
 *    boca); sobre el verde Namek eso apenas se distinguía. El factor de aquí oscurece además el
 *    tono de piel de esas capas en la raza indicada (1 = sin cambio).
 */
public final class RaceFaceTraits {
    private RaceFaceTraits() {}

    /** Oscurecido extra de las capas "skin" (boca, nariz) en Namekiano. Bajar = más oscuro. */
    public static final float NAMEKIAN_SKIN_FEATURE_SHADE = 0.6f;

    public static boolean hasEyebrows(Race race) {
        return switch (race) {
            case NAMEKIAN, ARCOSIAN, MAJIN -> false;
            default -> true;
        };
    }

    public static float skinFeatureShade(Race race) {
        return race == Race.NAMEKIAN ? NAMEKIAN_SKIN_FEATURE_SHADE : 1f;
    }

    /** Tono de piel para una capa "skin" de la cara en esta raza. */
    public static int shadeSkin(Race race, int rgb) {
        float f = skinFeatureShade(race);
        if (f >= 1f) return rgb & 0xFFFFFF;
        int r = Math.round(((rgb >> 16) & 0xFF) * f);
        int g = Math.round(((rgb >> 8) & 0xFF) * f);
        int b = Math.round((rgb & 0xFF) * f);
        return (r << 16) | (g << 8) | b;
    }

    /** ¿Se pinta esta capa de este rasgo en esta raza? (cejas fuera en razas sin cejas) */
    public static boolean shows(Race race, FaceCategory category, FaceLayer layer) {
        return hasEyebrows(race) || (category != FaceCategory.EYEBROWS && !layer.eyebrow());
    }
}
