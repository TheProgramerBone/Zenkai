package com.hmc.zenkai.feature.aura;

/**
 * Resultado visual FINAL. Es lo único que recibe el renderer: no ve PlayerStatsAttachment,
 * ni formas, ni razas, ni TP, ni Kaioken. Si mañana se añade una transformación nueva o
 * una raza nueva, el renderer no se entera.
 *
 * TRES BLOQUES, y la separación importa:
 *
 *   FORMA     mass..density   -> lo que un AuraModifier PUEDE tocar
 *   PODER     size/alpha/core -> lo que NINGÚN AuraModifier puede tocar
 *   CAPAS     wisps..ground   -> tampoco; son densidad de efecto, o sea poder
 *
 * El bloque de PODER se calcula solo desde AuraState. Esa es la implementación literal
 * de "la raza modifica la personalidad, nunca el poder": no es una convención que haya
 * que recordar al revisar un PR, es que no existe el campo por el que romperla.
 *
 * Se midió: con estado, tinte, size, alpha y core idénticos, las cinco firmas raciales
 * quedan dentro de un rango de 7.4% de masa visual, sobre un suelo de medición de 3.9%
 * (ruido inter-semilla del generador de siluetas). Afinar por debajo de eso sería
 * perseguir ruido — ver AuraTuning.NEUTRALITY_TOLERANCE.
 */
public record AuraProfile(
        // ── FORMA: modificable por forma y raza ─────────────────────────────
        float mass,
        float spike,
        float turbulence,
        float spread,
        float height,
        float density,
        // ── COMPORTAMIENTO ──────────────────────────────────────────────────
        float pulseHz,
        float pulseAmp,
        /** Ticks por frame de la hoja. Acelera con turbulence Y con presence. */
        float frameTicks,
        /** Grados/segundo que gira el anillo de suelo. */
        float groundSpin,
        // ── PODER: NO modificable. Solo depende de AuraState. ───────────────
        float size,
        float alpha,
        float core,
        int wisps,
        float sparksPerSecond,
        float ground,
        /** FORMA, no PODER: pide una pasada extra aditiva sobre el cono (ver
         *  AuraSkirtRenderer). No mueve size/alpha/core — solo decide una técnica de
         *  dibujado, igual que mass/spike/... Ver el javadoc del campo homónimo en
         *  AuraModifier para el porqué (prueba de viabilidad 2026-09-02). */
        boolean additiveGlow,
        /** FORMA, no PODER: cambia la geometría de las chispas a un rayo quebrado
         *  aditivo (ver AuraSparkRenderer y el javadoc del campo homónimo en
         *  AuraModifier). No mueve sparksPerSecond — solo cómo se dibuja cada una. */
        boolean electricSparks,
        /** FORMA, no PODER: activa el emisor de ascuas con textura propia (ver
         *  AuraEmberRenderer y el javadoc del campo homónimo en AuraModifier). */
        boolean fireEmbers
) {
    /** Perfil apagado. El renderer puede descartarlo sin mirar nada más. */
    public static final AuraProfile OFF =
            new AuraProfile(AuraTuning.C_MASS, AuraTuning.C_SPIKE, AuraTuning.C_TURB,
                    AuraTuning.C_SPREAD, AuraTuning.C_HEIGHT, AuraTuning.C_DENSITY,
                    AuraTuning.PULSE_HZ_BASE, AuraTuning.PULSE_AMP_BASE,
                    AuraTuning.FRAME_TICKS_BASE, AuraTuning.GROUND_SPIN_BASE,
                    0f, 0f, 0f, 0, 0f, 0f, false, false, false);

    /**
     * Constructor compacto: clampa por completo. Un spread de -0.3 o un density de 2.7 venidos
     * de un datapack no llegan al renderer — se recortan aquí y punto. Es imposible
     * construir un AuraProfile inválido, así que el renderer no necesita defenderse.
     */
    public AuraProfile {
        mass = AuraTuning.clamp(mass, AuraTuning.MIN_MASS, AuraTuning.MAX_MASS);
        spike = AuraTuning.clamp(spike, AuraTuning.MIN_SPIKE, AuraTuning.MAX_SPIKE);
        turbulence = AuraTuning.clamp(turbulence, AuraTuning.MIN_TURB, AuraTuning.MAX_TURB);
        spread = AuraTuning.clamp(spread, AuraTuning.MIN_SPREAD, AuraTuning.MAX_SPREAD);
        height = AuraTuning.clamp(height, AuraTuning.MIN_HEIGHT, AuraTuning.MAX_HEIGHT);
        density = AuraTuning.clamp(density, AuraTuning.MIN_DENSITY, AuraTuning.MAX_DENSITY);

        pulseHz = AuraTuning.clamp(pulseHz, 0.05f, 12f);
        pulseAmp = AuraTuning.clamp(pulseAmp, 0f, 0.6f);
        frameTicks = AuraTuning.clamp(frameTicks,
                AuraTuning.FRAME_TICKS_MIN, AuraTuning.FRAME_TICKS_MAX);
        groundSpin = AuraTuning.clamp(groundSpin, 0f, 360f);

        size = AuraTuning.clamp(size, 0f, 3f);
        // 2.5 y no 2.0: la compensacion de la banda SIGNATURE (x1.70) sobre un alpha
        // base de 1.18 da 2.006, y un techo de 2.0 recortaba justo el caso para el que
        // esa compensacion existe. Lo detecto AuraLodSelfTest.
        alpha = AuraTuning.clamp(alpha, 0f, 2.5f);
        core = AuraTuning.clamp(core, 0f, 1.5f);
        wisps = Math.max(0, Math.min(256, wisps));
        sparksPerSecond = AuraTuning.clamp(sparksPerSecond, 0f, 200f);
        ground = AuraTuning.clamp01(ground);
    }

    public boolean isVisible() { return size > 0.01f && alpha > 0.01f; }

    /**
     * Perfil atenuado a una fracción de visibilidad, para el ajuste de opacidad en primera
     * persona (ver ClientConfig#auraFirstPersonOpacityFrac). Escala el bloque de PODER
     * igual que hace {@link AuraLod#compensate}, así que un 50% se lee como "la mitad de
     * fuerte" y no como una forma distinta — la FORMA (mass..density) no se toca.
     */
    public AuraProfile faded(float frac) {
        return new AuraProfile(mass, spike, turbulence, spread, height, density,
                pulseHz, pulseAmp, frameTicks, groundSpin,
                size, alpha * frac, core * frac,
                Math.round(wisps * frac), sparksPerSecond * frac, ground * frac,
                additiveGlow, electricSparks, fireEmbers);
    }

    /**
     * Tamaño en el instante t de la pulsación. Kaioken es el único que hace que esto
     * se note: sin él la amplitud es ±4% a 0.6 Hz (respiración), a x20 es ±12% a 1.68 Hz.
     * @param seconds tiempo continuo en segundos (gameTime/20 + partialTick/20)
     */
    public float pulsedSize(float seconds) {
        return size * (1f + pulseAmp * (float) Math.sin(seconds * pulseHz * 2f * Math.PI));
    }
}