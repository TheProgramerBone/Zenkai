package com.hmc.zenkai.feature.race.face;

import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * Categorías de rasgo facial. Conjunto CERRADO en código a propósito: la GUI y el renderer
 * necesitan conocerlas, y añadir una es raro y exige GUI de todas formas. Lo abierto (datapack,
 * addons) es el CONTENIDO de cada categoría — ver {@link FaceCatalog}.
 *
 * {@link #key()} es a la vez la subcarpeta del datapack (zenkai_face_parts/&lt;key&gt;/), el primer
 * segmento del id (zenkai:eyes/...) y la clave en el NBT del jugador. No cambiarla nunca: rompería
 * saves y addons.
 *
 * El ORDEN de declaración es el orden de pintado (FaceOverlayGeoLayer): a igual profundidad gana
 * lo último, así que las cejas van después de los ojos para poder pisar el párpado.
 *
 * Las cicatrices/tatuajes/maquillaje/marcas NO van aquí: serán "decals" aparte (varios a la vez,
 * con color propio), decisión C5 de .claude/docs/customizacion-cara-y-pelo.md (decisiones cerradas).
 */
public enum FaceCategory {
    /** Sin "ninguno": no existe una cara sin ojos. */
    EYES("eyes", false, FaceLayer.Color.EYE),
    /** "Ninguna" permitida. Por defecto siguen el color de pelo (y el hair_rgb de la forma). */
    EYEBROWS("eyebrows", true, FaceLayer.Color.HAIR),
    /** "Ninguno" permitido (las caras base ya traen el hueco de la nariz pintado). */
    NOSE("nose", true, FaceLayer.Color.SKIN),
    /** Sin "ninguno": no existe una cara sin boca. */
    MOUTH("mouth", false, FaceLayer.Color.SKIN);

    private final String key;
    private final boolean allowsNone;
    private final FaceLayer.Color defaultColor;

    FaceCategory(String key, boolean allowsNone, FaceLayer.Color defaultColor) {
        this.key = key;
        this.allowsNone = allowsNone;
        this.defaultColor = defaultColor;
    }

    public String key()          { return key; }
    public boolean allowsNone()  { return allowsNone; }
    /** Color de una capa que no lo declara (incluido el atajo "texture" de una sola capa). */
    public FaceLayer.Color defaultColor() { return defaultColor; }

    @Nullable
    public static FaceCategory byKey(String key) {
        if (key == null) return null;
        String k = key.toLowerCase(Locale.ROOT);
        for (FaceCategory c : values()) if (c.key.equals(k)) return c;
        return null;
    }
}
