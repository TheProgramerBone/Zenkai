package com.hmc.zenkai.client.render_and_model_entities.kivfx;

/**
 * SEGUNDO COLOR de la técnica que se está dibujando AHORA (2026-09-24). Solo cliente, hilo de
 * render.
 * <p>
 * Qué es: el color del INTERIOR — la capa caliente de la rampa de cuatro capas de
 * {@code ki_energy.fsh} y el núcleo explícito. Sin él (-1) esa capa se DERIVA del primer color
 * ({@link #derivedHot}: azul → cian, naranja → amarillo), que es el comportamiento de siempre.
 * Con él, una técnica puede ser, por ejemplo, morada por fuera y rosa por dentro.
 * <p>
 * Por qué un estado "actual" y no un parámetro más: el color principal viaja por VÉRTICE, pero el
 * segundo solo lo necesitan el shader (como uniform) y los pocos sitios que calculan el color del
 * núcleo; pasarlo por cada firma de KiVfxCompositeRenderer duplicaba media docena de sobrecargas.
 * Cada tarea de dibujo (proyectil, bola de carga, afterglow, vista previa del editor) llama a
 * {@link #begin} al EMPEZAR con su propio valor — también con -1 —, así que un valor de otra
 * técnica nunca se arrastra: KiVfxFrameQueue reproduce las tareas de una en una y vuelca el lote
 * de cada una antes de la siguiente, lo que también hace seguro el uniform por técnica.
 */
public final class KiVfxColors {
    private KiVfxColors() {}

    private static int secondary = -1;

    /** Fija el segundo color de la técnica que se va a dibujar; -1 = ninguno. */
    public static void begin(int rgb2) {
        secondary = rgb2 < 0 ? -1 : (rgb2 & 0xFFFFFF);
    }

    /** -1 si la técnica actual no tiene segundo color. */
    public static int secondary() { return secondary; }

    /** Capa caliente/interior de la técnica actual: el segundo color si lo hay, si no el derivado. */
    public static float[] hot(float r, float g, float b) {
        if (secondary < 0) return derivedHot(r, g, b);
        return new float[]{((secondary >> 16) & 0xFF) / 255f, ((secondary >> 8) & 0xFF) / 255f,
                (secondary & 0xFF) / 255f};
    }

    private static final float HOT_POW = 0.45f;

    /**
     * Capa CALIENTE derivada del tinte — espejo exacto de {@code hot} en ki_energy.fsh (ver
     * "RAMPA DE CUATRO CAPAS" allí): mismo tono, normalizado al canal máximo y con los secundarios
     * levantados. Azul → cian, naranja → amarillo, magenta → rosa. Mantenerlas iguales: el núcleo
     * explícito y la cáscara tienen que hablar el mismo idioma de color.
     */
    public static float[] derivedHot(float r, float g, float b) {
        float m = Math.max(Math.max(r, g), Math.max(b, 1.0e-3f));
        return new float[]{
                (float) Math.pow(Math.min(1f, r / m), HOT_POW),
                (float) Math.pow(Math.min(1f, g / m), HOT_POW),
                (float) Math.pow(Math.min(1f, b / m), HOT_POW)};
    }
}
