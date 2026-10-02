package com.hmc.zenkai.feature.race.face;

/**
 * Dónde está la cara FRONTAL de una cabeza en la textura de su cuerpo: rectángulo con esquinas
 * INCLUSIVAS (x0,y0 = primer píxel de la cara, x1,y1 = último), en píxeles de una textura de
 * {@code texSize}×{@code texSize}.
 *
 * Cada rasgo facial (ojos, cejas, nariz, boca, pelo plano) se dibuja UNA vez en la cara
 * canónica {@link #CANONICAL} (16,16)-(31,31) de un PNG de 256, y se recoloca a la región de
 * cada modelo: misma medida = solo se traslada; otra medida = se escala por vecino más cercano
 * (sin suavizar, decisión del usuario 2026-10-01). La tabla a mano vive en {@link FaceRegions}.
 */
public record FaceRegion(int x0, int y0, int x1, int y1, int texSize) {

    /** Lado de la cara canónica en texels y su esquina en la textura de 256. */
    public static final int FACE = 16, ORIGIN = 16, CANONICAL_TEX = 256;

    public static final FaceRegion CANONICAL = new FaceRegion(16, 16, 31, 31);

    public FaceRegion {
        if (x1 < x0 || y1 < y0) throw new IllegalArgumentException("región vacía: " + x0 + "," + y0 + " → " + x1 + "," + y1);
        if (x0 < 0 || y0 < 0 || x1 >= texSize || y1 >= texSize)
            throw new IllegalArgumentException("región fuera de la textura de " + texSize + ": " + x0 + "," + y0 + " → " + x1 + "," + y1);
    }

    /** Región en una textura de 256 (la medida de cada textura de raza de hoy). */
    public FaceRegion(int x0, int y0, int x1, int y1) {
        this(x0, y0, x1, y1, CANONICAL_TEX);
    }

    public int width()  { return x1 - x0 + 1; }
    public int height() { return y1 - y0 + 1; }

    public boolean isCanonical() { return equals(CANONICAL); }

    /**
     * Texel de la cara canónica (en la textura de 256) que se copia al píxel (tx, ty) de esta
     * región. Vecino más cercano: con 16 de lado es una traslación exacta; con 8 toma una fila de
     * cada dos. Fuera de la región devuelve null.
     */
    public int[] sourceTexel(int tx, int ty) {
        if (tx < x0 || tx > x1 || ty < y0 || ty > y1) return null;
        return new int[] {ORIGIN + (tx - x0) * FACE / width(), ORIGIN + (ty - y0) * FACE / height()};
    }
}
