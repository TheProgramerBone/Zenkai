package com.hmc.zenkai.client.render_and_model_entities.ki;

import com.hmc.zenkai.feature.technique.KiTechniqueType;

/**
 * AUTORIDAD ÚNICA de cómo SE VE cada tipo de técnica de ki. Solo cliente.
 * Antes esto estaba repartido en tres sitios que no se hablaban: la forma en {@link KiShape},
 * el largo y los alfas hardcodeados dentro del renderer, y la estela dentro de
 * {@code KiTechniqueType} — que es código común y de identidad, y cuyo propio Javadoc ya
 * admitía que la estela "es visual, no balance". Es la misma división que causó el lío de
 * TechniquePosition.
 * QUÉ NO ENTRA AQUÍ: nada que el servidor necesite. El tamaño del proyectil, el radio de
 * explosión y el factor de área siguen en {@link KiTechniqueType} porque son física, y los
 * números de balance siguen en el datapack. Esto es dirección de arte y NO se expone a
 * TechniqueDef ni al sistema de overrides por comandos.
 * CÓMO SE LEE UNA ENTRADA. La energía de Dragon Ball tiene tres zonas y ningún borde: núcleo
 * blanco pequeño, cuerpo del color de la técnica, y un límite que se disuelve. Los tres números
 * de {@code bands} dicen DÓNDE están las fronteras y {@code edgeFade} cuánto tarda en
 * desaparecer el borde. Para una esfera la coordenada de banda es exactamente el radio en
 * pantalla: {@code bandCore 0.75} significa "blanco dentro del 25 % central".
 * EL HALO ES SOLO PARA FORMAS COMPACTAS. Es un billboard centrado en la entidad: en una bola
 * coincide con el cuerpo y funciona, pero en un haz aparece como un disco plano clavado en
 * mitad del chorro. Las formas alargadas llevan {@code haloAlpha 0} y su brillo exterior lo
 * pone la envolvente, que es geometría y sigue la silueta.
 */
public record KiVisual(
        // ── Cuerpo ──────────────────────────────────────────────────────────
        KiShape shape,
        /** Longitud HORNEADA en la malla, en unidades de grosor. Ya no se escala Z aparte. */
        float meshLength,
        /** Grosor característico: radio del tubo (CYLINDER) o radio de giro (HELIX).
         *  La esfera y el disco lo ignoran: siempre miden 0.5. */
        float meshRadius,
        /** Radio de la bola en la punta, 0 = sin cabeza. */
        float headScale,
        /** true = la punta de la malla está en la entidad y el cuerpo se extiende hacia atrás.
         *  Obligatorio en los haces: centrado, medio haz sobresale POR DELANTE del proyectil. */
        boolean anchorTip,
        /** Modo de rampa del shader: cómo se decide dónde está el núcleo. */
        Band band,
        /** Alfa del cuerpo. Bajo = energía; alto = plástico. */
        float shellAlpha,

        // ── Envolvente ──────────────────────────────────────────────────────
        /** La misma malla agrandada, con alfa mínimo. Da el límite gaseoso EN 3D, que un
         *  billboard no puede dar porque siempre encara a la cámara. 0 = sin envolvente. */
        float envelopeScale,
        float envelopeAlpha,

        // ── Núcleo de la ruta de respaldo ───────────────────────────────────
        /** El shader no los usa: pinta las bandas por píxel sobre una sola emisión. */
        float coreScale,
        float coreAlpha,

        // ── Rampa del shader ────────────────────────────────────────────────
        /** Niveles de los tres límites, de dentro a fuera. Ver ki_fresnel.fsh. */
        float bandCore, float bandBorder, float bandOutline,
        /** Cuánto se lava el núcleo a blanco (0 = tinte puro, 1 = blanco). */
        float coreWhite,
        /** Cuánto se oscurece el contorno respecto al tinte. Valores bajos en tintes pálidos
         *  producen un anillo pardo alrededor: no todas las formas quieren contorno profundo. */
        float outlineDark,
        /** Anchura del desvanecido del borde. Pequeño = silueta dura de objeto sólido. */
        float edgeFade,
        /** Amplitud del hervor. 0 deja las bandas quietas. */
        float wobble,

        // ── Halo ────────────────────────────────────────────────────────────
        /** Diámetro del halo como múltiplo del ancho del proyectil. */
        float haloScale,
        float haloAlpha,

        // ── Estela ──────────────────────────────────────────────────────────
        /** 0 = sin estela. Nunca supera {@code KiProjectileEntity.TRAIL_MAX}. */
        int trailPoints,
        /** Ancho de la capa exterior como múltiplo del ancho del proyectil. */
        float trailWidth,
        /** Opacidad en la cabeza de la cinta. Estaba fija en el renderer e igual para las nueve
         *  técnicas: una bola lenta y enorme necesita una bruma tenue, no el mismo trazo opaco
         *  que un láser. */
        float trailAlpha,
        /** Ancho de la capa interior (el núcleo blanco) respecto a la exterior. 0 = sin núcleo. */
        float trailInnerMul,
        /** Velocidad de desplazamiento de las UV a lo largo de la cinta. */
        float trailScroll,

        // ── Partículas ──────────────────────────────────────────────────────
        /** Chispas por tick a tamaño 1. Escala con el tamaño en el renderer. */
        float sparkRate
) {

    /** Cómo decide el shader dónde está el núcleo. El ordinal viaja al shader como float. */
    public enum Band {
        /** Superficie cerrada: núcleo donde encara la cámara (esfera, tubo, hélice). */
        SURFACE,
        /** Superficie plana: núcleo en el centro geométrico, por UV (disco). */
        RADIAL,
        /** Invertido: brilla en el filo y se abre de frente (burbuja). */
        RIM
    }

    // ── Tabla por tipo ──────────────────────────────────────────────────────

    private static final KiVisual[] BY_TYPE = new KiVisual[KiTechniqueType.values().length];

    static {
        // WAVE (Kamehameha, Final Flash, Galick Gun): haz LIMPIO. Bulbo en la punta, tubo que
        // se estrecha hacia atrás, y nada más. Llevaba cintas enroscadas y le robaban la
        // identidad al spiral además de ensuciar la silueta.
        put(KiTechniqueType.WAVE, b(KiShape.CYLINDER)
                .mesh(3.6f, 0.20f, 0.30f).anchored().shell(0.58f).envelope(1.55f, 0.17f).core(0.62f, 0.85f)
                .bands(0.72f, 0.30f, 0.05f).tone(0.94f, 0.50f).edge(0.28f).wobble(0.95f)
                .halo(0f, 0f).trail(18, 2.0f, 0.72f, 0.30f, 1.1f).sparks(0.55f));

        // LAZER: el mismo haz, mucho más fino y largo, con la cabeza apenas marcada.
        put(KiTechniqueType.LAZER, b(KiShape.CYLINDER)
                .mesh(7.0f, 0.075f, 0.12f).anchored().shell(0.68f).envelope(2.0f, 0.13f).core(0.55f, 0.90f)
                .bands(0.62f, 0.26f, 0.04f).tone(0.97f, 0.60f).edge(0.30f).wobble(0.55f)
                .halo(0f, 0f).trail(34, 0.9f, 0.85f, 0.30f, 2.2f).sparks(0.20f));

        // SPIRAL: la hélice es SUYA y de nadie más. Vuela recto — el nombre viene de la forma,
        // no de la trayectoria.
        put(KiTechniqueType.SPIRAL, b(KiShape.HELIX)
                .mesh(2.4f, 0.28f, 0.26f).anchored().shell(0.56f).envelope(1.32f, 0.15f).core(0.62f, 0.85f)
                .bands(0.70f, 0.28f, 0.06f).tone(0.92f, 0.48f).edge(0.26f).wobble(1.35f)
                .halo(0f, 0f).trail(26, 1.3f, 0.68f, 0.32f, 1.5f).sparks(0.65f));

        // BLAST: la bola estándar. Núcleo blanco DENTRO del 25 % central y lo demás del
        // color de la técnica: al revés, el tinte del jugador se pierde.
        put(KiTechniqueType.BLAST, b(KiShape.SPHERE)
                .shell(0.50f).envelope(1.35f, 0.15f).core(0.62f, 0.85f)
                .bands(0.75f, 0.30f, 0.05f).tone(0.90f, 0.55f).edge(0.30f).wobble(1.0f)
                .halo(2.2f, 0.24f).trail(12, 1.0f, 0.70f, 0.30f, 1.0f).sparks(0.35f));

        // BIG_BLAST: enorme y lento. Estela CORTA, TENUE y SIN núcleo blanco: con los alfas del
        // resto salía una cuña blanca opaca detrás de la bola, que a este tamaño se lee como un
        // recorte pegado encima en vez de como aire desplazado.
        put(KiTechniqueType.BIG_BLAST, b(KiShape.SPHERE)
                .shell(0.46f).envelope(1.45f, 0.16f).core(0.66f, 0.88f)
                .bands(0.78f, 0.32f, 0.04f).tone(0.92f, 0.48f).edge(0.34f).wobble(0.70f)
                .halo(2.8f, 0.26f).trail(9, 1.5f, 0.42f, 0f, 0.6f).sparks(0.85f));

        // BURST: muchas bolas pequeñas. Se reduce para que la ráfaga no sature.
        put(KiTechniqueType.BURST, b(KiShape.SPHERE)
                .shell(0.52f).envelope(1.30f, 0.13f).core(0.60f, 0.85f)
                .bands(0.72f, 0.28f, 0.06f).tone(0.88f, 0.55f).edge(0.28f).wobble(1.1f)
                .halo(1.8f, 0.20f).trail(8, 0.85f, 0.60f, 0.30f, 1.0f).sparks(0.18f));

        // DISK: una CUCHILLA, no una bola aplastada. La lente tiene canto (ver KiMeshFactory) y
        // ese canto lleva coordenada de banda alta, así que el fresnel lo pinta casi blanco: el
        // filo incandescente es lo que lo define. Por eso el contorno va apenas oscurecido
        // (0.78) — con valores bajos aparecía un anillo pardo alrededor que ensuciaba el borde
        // justo donde está la lectura del arma. Sin estela: un filo que corta no deja rastro.
        put(KiTechniqueType.DISK, b(KiShape.DISK)
                .band(Band.RADIAL).shell(0.74f).envelope(1.12f, 0.16f).core(0.62f, 0.90f)
                .bands(0.60f, 0.26f, 0.06f).tone(0.92f, 0.78f).edge(0.10f).wobble(0.85f)
                .halo(0f, 0f).trail(0, 0f, 0f, 0f, 0f).sparks(0.30f));

        // BARRIER: burbuja. RIM invierte el fresnel — filo encendido, centro abierto. Sin núcleo
        // ni envolvente: cualquiera de los dos la convierte en una bola y deja de leerse como
        // cristal. No viaja, así que no lleva estela.
        put(KiTechniqueType.BARRIER, b(KiShape.SPHERE)
                .band(Band.RIM).shell(0.40f).envelope(0f, 0f).core(0f, 0f)
                .bands(0.55f, 0.22f, 0.05f).tone(0.85f, 0.62f).edge(0.30f).wobble(0.55f)
                .halo(1.5f, 0.14f).trail(0, 0f, 0f, 0f, 0f).sparks(0.10f));

        // EXPLOSION: no viaja; es la mecha antes de detonar. Hervor alto y envolvente ancha —
        // lo que tiene que comunicar es inestabilidad inminente.
        put(KiTechniqueType.EXPLOSION, b(KiShape.SPHERE)
                .shell(0.46f).envelope(1.50f, 0.18f).core(0.68f, 0.90f)
                .bands(0.76f, 0.32f, 0.04f).tone(0.94f, 0.50f).edge(0.36f).wobble(1.5f)
                .halo(3.0f, 0.30f).trail(0, 0f, 0f, 0f, 0f).sparks(1.20f));

        // Red de seguridad: un tipo nuevo sin entrada se dibuja como una bola estándar en vez
        // de reventar con un null.
        for (int i = 0; i < BY_TYPE.length; i++) {
            if (BY_TYPE[i] == null) BY_TYPE[i] = b(KiShape.SPHERE).build();
        }
    }

    public static KiVisual of(KiTechniqueType type) { return BY_TYPE[type.ordinal()]; }

    /** ¿Este tipo dibuja estela? Sustituye a KiTechniqueType.hasTrail(). */
    public boolean hasTrail() {
        return trailPoints > 0 && trailWidth > 0f && trailAlpha > 0f;
    }

    /** El núcleo blanco de la cinta es opcional: en cuerpos grandes y lentos sobra. */
    public boolean hasTrailCore() { return trailInnerMul > 0f; }

    public boolean hasEnvelope() { return envelopeScale > 1f && envelopeAlpha > 0f; }

    /** El valor que espera el uniform ZenkaiShape. */
    public float bandMode() { return band.ordinal(); }

    // ── Constructor fluido ──────────────────────────────────────────────────

    private static void put(KiTechniqueType type, Builder b) {
        BY_TYPE[type.ordinal()] = b.build();
    }

    private static Builder b(KiShape shape) { return new Builder(shape); }

    /**
     * Los defaults son los de BLAST. Cada tipo declara solo lo que lo diferencia, que es lo que
     * hace que la tabla se lea como decisiones de arte y no como veinte números por línea.
     */
    private static final class Builder {
        private final KiShape shape;
        private Band band = Band.SURFACE;
        private float meshLength = 1.0f, meshRadius = 0.5f, headScale = 0f;
        private boolean anchorTip = false;
        private float shellAlpha = 0.50f;
        private float envelopeScale = 1.35f, envelopeAlpha = 0.15f;
        private float coreScale = 0.62f, coreAlpha = 0.85f;
        private float bandCore = 0.75f, bandBorder = 0.30f, bandOutline = 0.05f;
        private float coreWhite = 0.90f, outlineDark = 0.55f, edgeFade = 0.30f, wobble = 1.0f;
        private float haloScale = 2.2f, haloAlpha = 0.24f;
        private int trailPoints = 0;
        private float trailWidth = 0f, trailAlpha = 0.70f, trailInnerMul = 0.30f, trailScroll = 1.0f;
        private float sparkRate = 0.35f;

        Builder(KiShape shape) { this.shape = shape; }

        Builder band(Band v) { this.band = v; return this; }
        Builder mesh(float length, float radius, float head) {
            meshLength = length; meshRadius = radius; headScale = head; return this;
        }
        Builder anchored() { this.anchorTip = true; return this; }
        Builder shell(float v) { this.shellAlpha = v; return this; }
        Builder envelope(float scale, float alpha) { envelopeScale = scale; envelopeAlpha = alpha; return this; }
        Builder core(float scale, float alpha) { coreScale = scale; coreAlpha = alpha; return this; }
        Builder bands(float c, float b, float o) { bandCore = c; bandBorder = b; bandOutline = o; return this; }
        Builder tone(float white, float dark) { coreWhite = white; outlineDark = dark; return this; }
        Builder edge(float v) { this.edgeFade = v; return this; }
        Builder wobble(float v) { this.wobble = v; return this; }
        Builder halo(float scale, float alpha) { haloScale = scale; haloAlpha = alpha; return this; }
        Builder trail(int points, float width, float alpha, float innerMul, float scroll) {
            trailPoints = points; trailWidth = width; trailAlpha = alpha;
            trailInnerMul = innerMul; trailScroll = scroll;
            return this;
        }
        Builder sparks(float v) { this.sparkRate = v; return this; }

        KiVisual build() {
            return new KiVisual(shape, meshLength, meshRadius, headScale, anchorTip, band,
                    shellAlpha, envelopeScale, envelopeAlpha, coreScale, coreAlpha,
                    bandCore, bandBorder, bandOutline, coreWhite, outlineDark, edgeFade, wobble,
                    haloScale, haloAlpha,
                    trailPoints, trailWidth, trailAlpha, trailInnerMul, trailScroll, sparkRate);
        }
    }
}