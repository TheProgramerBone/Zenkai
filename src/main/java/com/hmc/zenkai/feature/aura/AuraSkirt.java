package com.hmc.zenkai.feature.aura;

/**
 * Un anillo de planos-silueta. El aura es una pila de estos: cada faldón aporta una
 * franja de altura, y el conjunto forma la "gota".
 * Los planos NO son billboards hacia cámara — están rotados en anillo alrededor del eje
 * Y, y cada uno mapea un cuadrante de la hoja de llamas. Eso es lo que hace que el aura
 * tenga volumen real en vez de parecer una calcomanía.
 * @param count     planos del anillo. Por debajo de 5 el cono deja de leerse como
 *                  volumen y se ven los quads sueltos: ese es el suelo real del LOD,
 *                  no una cifra que se pueda bajar por presupuesto.
 * @param offsetDeg desfase angular del anillo, para que los faldones no se alineen.
 * @param tiltDeg   inclinación de los planos. Positivo abre hacia fuera (falda),
 *                  negativo cierra hacia dentro (punta).
 * @param baseR     radio del anillo, en bloques.
 * @param width     ancho de cada plano, en bloques.
 * @param height    alto de cada plano, en bloques.
 * @param yStart    altura a la que arranca el faldón. Los que empiezan por encima de 0
 *                  necesitan que la textura disuelva su borde inferior, o se ve el
 *                  corte recto del quad en pleno aire.
 * @param jitter    variación de altura por plano, cuantizada al step de textura.
 * @param alpha     peso del faldón dentro del conjunto.
 * @param tex       cuadrante de la hoja: 0 llama ancha, 1 llama alta, 2 penacho,
 *                  3 faldón bajo.
 */
public record AuraSkirt(
        int count,
        float offsetDeg,
        float tiltDeg,
        float baseR,
        float width,
        float height,
        float yStart,
        float jitter,
        float alpha,
        int tex
) {
    /** Suelo por debajo del cual el anillo deja de leerse como volumen. */
    public static final int MIN_COUNT = 5;

    /** Copia con menos planos, respetando MIN_COUNT. La usa el LOD. */
    public AuraSkirt withCountFactor(float factor) {
        int n = Math.max(MIN_COUNT, Math.round(count * factor));
        return n == count ? this
                : new AuraSkirt(n, offsetDeg, tiltDeg, baseR, width, height,
                yStart, jitter, alpha, tex);
    }

    /** Quads que cuesta dibujar este faldón, contando masa + núcleo. */
    public int quadCost() { return count * 2; }
}