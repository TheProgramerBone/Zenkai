package com.hmc.zenkai.client.render_and_model_entities.kivfx;

import com.hmc.zenkai.feature.technique.KiTechniqueType;

/**
 * AUTORIDAD ÚNICA de cómo SE VE cada tipo de técnica de ki. Solo cliente.
 *
 * UNA TÉCNICA ES UNA COMPOSICIÓN, NO UN RENDERER. Este record no tiene lógica de dibujo — es
 * configuración pura, agrupada en los mismos componentes visuales que dibuja el pipeline
 * ({@link Shell}, {@link Core}, {@link Envelope}, {@link Ribbon} para la estela, {@link Rays},
 * {@link Halo}, {@link Particles}). Añadir una técnica nueva es rellenar una fila de esta tabla,
 * nunca escribir una clase nueva.
 *
 * CÓMO SE LEE UN {@link Shell}. La energía de Dragon Ball tiene tres zonas y ningún borde: núcleo
 * blanco pequeño, cuerpo del color de la técnica, y un límite que se disuelve. Los tres números de
 * {@code bandCore}/{@code bandBorder}/{@code bandOutline} dicen DÓNDE están las fronteras y
 * {@code edgeFade} cuánto tarda en desaparecer el borde. Para una esfera la coordenada de banda es
 * exactamente el radio en pantalla: {@code bandCore 0.75} significa "blanco dentro del 25 %
 * central" — ver {@code ki_energy.fsh}.
 *
 * EL NÚCLEO ({@link Core}) ES GEOMETRÍA, NO UNA BANDA DE SHADER. Antes el centro blanco de
 * cualquier forma era una banda calculada por el fragment shader con {@code dot(V,N)}: correcta
 * para una esfera (un punto de tamaño angular CONSTANTE), pero rota para un tubo largo mirado casi
 * a lo largo de su propio eje — TODA la longitud visible puede leer "de frente" a la vez y la
 * banda cubre el haz entero en vez de un hilo por el centro (el bug de cámara real de este
 * sistema, confirmado con vídeos del usuario). Una malla real, pequeña y aparte, pintada con
 * aditivo, no tiene ese problema por construcción: su ancho en pantalla lo decide su propio radio
 * ({@link Core#scale()}), nunca un ángulo de vista. {@code coreAlpha 0} = esta técnica no lleva
 * núcleo explícito (BARRIER/DEATH_BALL: su {@link Band#RIM} ya pone el brillo en el FILO, un
 * núcleo central lo contradiría).
 *
 * EL HALO ({@link Halo}) ES SOLO PARA FORMAS COMPACTAS. Es un billboard centrado en la entidad: en
 * una bola coincide con el cuerpo y funciona, pero en un haz aparece como un disco plano clavado
 * en mitad del chorro. Las formas alargadas llevan {@code haloAlpha 0} y su brillo exterior lo
 * pone la {@link Envelope}, que es geometría real y sigue la silueta en vez de encarar siempre a
 * cámara.
 */
public record KiVfxProfile(
        KiVfxShape shape,
        Shell shell,
        Core core,
        Envelope envelope,
        Halo halo,
        Ribbon trail,
        Rays rays,
        Particles particles,
        /** true = la punta de la malla está en la entidad y el cuerpo se extiende hacia atrás.
         *  Obligatorio en los haces: centrado, medio haz sobresaldría POR DELANTE del proyectil. */
        boolean anchorTip,
        /** true = culling normal (solo la cara que mira a cámara) en vez de NO_CULL — para
         *  cáscaras SURFACE que la CÁMARA puede llegar a atravesar por dentro (hoy solo
         *  EXPLOSION; BARRIER es RIM y ya no lo necesita, ver su fila). Como las caras siempre miran hacia fuera del centro, con culling normal quien
         *  está DENTRO no ve ninguna y la cáscara deja de tapar la pantalla entera; quien la mira
         *  desde fuera la ve exactamente igual que con NO_CULL. */
        boolean backfaceCull,
        /** true = la estela sigue la MISMA torsión que hornea la malla HELIX en vez de una cinta
         *  plana — ver {@link KiVfxGeometry#helixAngleFromTip}. Solo tiene sentido con
         *  {@code shape() == HELIX}, pero es un flag EXPLÍCITO, no una inferencia por forma: deja
         *  a un futuro HELIX no-espiral no llevar esta estela sin ramificar por tipo. */
        boolean helixTrail
) {
    /** Cómo decide el fragment shader dónde está el núcleo de la CÁSCARA. El ordinal viaja como
     *  uniform float (ZenkaiShape en ki_energy.fsh). */
    public enum Band {
        /** Superficie cerrada: núcleo donde encara la cámara (esfera, tubo, hélice). */
        SURFACE,
        /** Superficie plana: núcleo en el centro geométrico, por UV (disco). */
        RADIAL,
        /** Invertido: brilla en el filo y se abre de frente (burbuja/RIM). */
        RIM
    }

    /** Cáscara + rampa de bandas del fragment shader. Ver la cabecera de la clase. */
    public record Shell(
            /** Longitud HORNEADA en la malla, en unidades de grosor — no se escala Z aparte (ver
             *  {@link KiVfxGeometry}: escalar Z estira el tubo de la hélice y la retuerce). */
            float meshLength,
            /** Radio del tubo (BEAM) o de giro (HELIX). La esfera y el disco lo ignoran. */
            float meshRadius,
            /** Radio de la bola en la punta, 0 = sin cabeza. */
            float headScale,
            float alpha,
            Band band,
            float bandCore, float bandBorder, float bandOutline,
            /** Cuánto se lava el núcleo a blanco (0 = tinte puro, 1 = blanco). */
            float coreWhite,
            /** Cuánto se oscurece el contorno respecto al tinte. */
            float outlineDark,
            /** Anchura del desvanecido del borde. Pequeño = silueta dura de objeto sólido. */
            float edgeFade,
            /** Amplitud del hervor procedural. 0 deja las bandas quietas. */
            float wobble,
            /** 0 = sin capa de detalle de superficie (la mayoría de técnicas). */
            float detailStrength,
            /** Qué textura usa la capa de detalle cuando detailStrength > 0 — irrelevante en
             *  0. Ver {@link DetailTexture}. */
            DetailTexture detailTexture
    ) {
        public float bandMode() { return band.ordinal(); }
    }

    /** Núcleo explícito: malla pequeña, aparte, aditiva. Ver la cabecera de la clase.
     *  @param offsetX, offsetY desplazamiento del núcleo respecto al centro, como fracción del
     *         tamaño de la técnica — 0,0 = centrado (la mayoría de tipos). Un núcleo DESPLAZADO
     *         hacia una esquina, en formas RIM (Death Ball, Supernova), imita el "sol con brillo
     *         descentrado" de las referencias (deathball_1-4, Supernova_1-6): un fresnel RIM
     *         simétrico por sí solo nunca produce ese punto caliente fuera de centro, hace falta
     *         geometría real desplazada — ver KiVfxCompositeRenderer.drawCore. */
    public record Core(float scale, float alpha, float offsetX, float offsetY) {
        public boolean enabled() { return alpha > 0f; }
    }

    /** La misma malla de la cáscara, agrandada, con alfa mínimo — el límite gaseoso EN 3D que un
     *  billboard no puede dar porque siempre encara a cámara. */
    public record Envelope(float scale, float alpha) {
        public boolean enabled() { return scale > 1f && alpha > 0f; }
    }

    /** Billboard aditivo centrado en la entidad. Ver "EL HALO" en la cabecera de la clase. */
    public record Halo(float scale, float alpha) {
        public boolean enabled() { return alpha > 0f; }
    }

    /** Estela: dos capas (exterior teñida + núcleo aditivo opcional) sobre el historial de
     *  posiciones del proyectil — dibujada por {@link KiRibbon}. */
    public record Ribbon(int points, float width, float alpha, float innerMul, float scroll) {
        public boolean enabled() { return points > 0 && width > 0f && alpha > 0f; }
        public boolean hasCore() { return innerMul > 0f; }
    }

    /** Rayos radiales rectos desde el centro — Death Ball/Supernova/Spirit Bomb. Misma geometría
     *  que la estela ({@link KiRibbon}), con un camino de 2 puntos en vez de un historial. */
    public record Rays(int count, float length, float alpha) {
        public boolean enabled() { return count > 0 && length > 0f && alpha > 0f; }
    }

    /** Chispas sueltas en vuelo y arcos eléctricos de carga/filo — ambas via ModParticles, ver
     *  KiVfxProjectileRenderer/KiVfxChargeRenderer. */
    public record Particles(float sparkRate, float chargeSparkRate) {}

    // ── Tabla por tipo ──────────────────────────────────────────────────────

    private static final KiVfxProfile[] BY_TYPE = new KiVfxProfile[KiTechniqueType.values().length];

    static {
        // Los números de esta tabla son los ya calibrados a ojo contra las referencias de
        // .claude/imagenes/ (ver .claude/pendiente/technique-visuals-referencia-mods.md) — se
        // portan tal cual a la nueva estructura de componentes; lo que cambia es la
        // ORGANIZACIÓN (Shell/Core/Envelope/Ribbon/Rays/Halo/Particles en vez de treinta campos
        // sueltos), no la dirección de arte. Recalibrar en juego sigue pendiente igual que antes.

        put(KiTechniqueType.WAVE, b(KiVfxShape.BEAM)
                .mesh(3.6f, 0.20f, 0.30f).anchored().shell(0.58f).envelope(1.55f, 0.17f).core(0.62f, 0.85f)
                .bands(0.72f, 0.30f, 0.05f).tone(0.94f, 0.50f).edge(0.30f).wobble(0.70f)
                .halo(0f, 0f).trail(18, 2.0f, 0.72f, 0.30f, 1.1f).sparks(0.55f));

        put(KiTechniqueType.LAZER, b(KiVfxShape.BEAM)
                .mesh(7.0f, 0.075f, 0.12f).anchored().shell(0.68f).envelope(2.0f, 0.13f).core(0.55f, 0.90f)
                .bands(0.62f, 0.26f, 0.04f).tone(0.97f, 0.60f).edge(0.28f).wobble(0.40f)
                .halo(0f, 0f).trail(34, 0.9f, 0.85f, 0.30f, 2.2f).sparks(0.20f));

        put(KiTechniqueType.SPIRAL, b(KiVfxShape.HELIX)
                .mesh(2.4f, 0.28f, 0.26f).anchored().shell(0.56f).envelope(1.32f, 0.15f).core(0.62f, 0.85f)
                .bands(0.70f, 0.28f, 0.06f).tone(0.92f, 0.48f).edge(0.28f).wobble(1.00f)
                .halo(0f, 0f).trail(26, 1.3f, 0.68f, 0.32f, 1.5f).helixTrail().sparks(0.65f));

        put(KiTechniqueType.BLAST, b(KiVfxShape.SPHERE)
                .shell(0.50f).envelope(1.35f, 0.15f).core(0.62f, 0.85f)
                .bands(0.75f, 0.30f, 0.05f).tone(0.90f, 0.55f).edge(0.32f).wobble(0.75f)
                .halo(2.2f, 0.24f).trail(12, 1.0f, 0.70f, 0.30f, 1.0f).detail(0.30f).sparks(0.35f));

        put(KiTechniqueType.BIG_BLAST, b(KiVfxShape.SPHERE)
                .shell(0.46f).envelope(1.45f, 0.16f).core(0.66f, 0.88f)
                .bands(0.78f, 0.32f, 0.04f).tone(0.92f, 0.48f).edge(0.36f).wobble(0.55f)
                .halo(2.8f, 0.26f).trail(9, 1.5f, 0.42f, 0f, 0.6f).sparks(0.85f));

        put(KiTechniqueType.BURST, b(KiVfxShape.SPHERE)
                .shell(0.52f).envelope(1.30f, 0.13f).core(0.60f, 0.85f)
                .bands(0.72f, 0.28f, 0.06f).tone(0.88f, 0.55f).edge(0.30f).wobble(0.80f)
                .halo(1.8f, 0.20f).trail(8, 0.85f, 0.60f, 0.30f, 1.0f).sparks(0.18f));

        put(KiTechniqueType.DISK, b(KiVfxShape.DISK)
                .band(Band.RADIAL).shell(0.74f).envelope(1.12f, 0.16f).core(0.62f, 0.90f)
                .bands(0.60f, 0.26f, 0.06f).tone(0.92f, 0.78f).edge(0.10f).wobble(0.45f)
                .halo(0f, 0f).trail(0, 0f, 0f, 0f, 0f).sparks(0.30f));

        put(KiTechniqueType.BARRIER, b(KiVfxShape.SPHERE)
                .band(Band.RIM).shell(0.40f).envelope(0f, 0f).core(0f, 0f)
                .bands(0.55f, 0.22f, 0.05f).tone(0.85f, 0.62f).edge(0.32f).wobble(0.35f)
                .halo(1.5f, 0.14f).trail(0, 0f, 0f, 0f, 0f).sparks(0.10f));
        // SIN backfaceCull desde 2026-09-24 (modelo de dragonminez, que dibuja su barrera sin
        // culling): Band.RIM ya usa |dot(V,N)|, así que desde DENTRO de la burbuja cada cara
        // encara la vista (f≈1 → g≈0) y el propio alfa del filo la desvanece — se lee como estar
        // dentro de un cristal, sin el salto brusco de "invisible dentro / aparece de golpe al
        // salir" que daba el culling. EXPLOSION sí lo conserva: es SURFACE, y desde dentro f≈1
        // significa NÚCLEO BLANCO en toda la pantalla.

        put(KiTechniqueType.EXPLOSION, b(KiVfxShape.SPHERE)
                .shell(0.46f).envelope(1.50f, 0.18f).core(0.68f, 0.90f)
                .bands(0.76f, 0.32f, 0.04f).tone(0.94f, 0.50f).edge(0.38f).wobble(1.10f)
                .halo(3.0f, 0.30f).trail(0, 0f, 0f, 0f, 0f).sparks(1.20f)
                .backfaceCull());

        put(KiTechniqueType.SPIRIT_BOMB, b(KiVfxShape.SPHERE)
                .shell(0.52f).envelope(1.45f, 0.20f).core(0.70f, 0.92f)
                .bands(0.85f, 0.34f, 0.05f).tone(0.96f, 0.40f).edge(0.30f).wobble(0.60f)
                .halo(2.6f, 0.28f).trail(22, 2.0f, 0.55f, 0.35f, 0.8f)
                .rays(6, 2.2f, 0.12f).sparks(1.00f));

        put(KiTechniqueType.KAMEHAMEHA, b(KiVfxShape.BEAM)
                .mesh(4.2f, 0.26f, 0.34f).anchored().shell(0.62f).envelope(1.60f, 0.20f).core(0.64f, 0.88f)
                .bands(0.70f, 0.30f, 0.05f).tone(0.95f, 0.45f).edge(0.34f).wobble(1.30f)
                .halo(0f, 0f).trail(20, 2.6f, 0.75f, 0.32f, 1.15f).sparks(0.70f));

        put(KiTechniqueType.FINAL_FLASH, b(KiVfxShape.BEAM)
                .mesh(4.6f, 0.42f, 0.46f).anchored().shell(0.64f).envelope(1.75f, 0.22f).core(0.68f, 0.90f)
                .bands(0.74f, 0.32f, 0.05f).tone(0.97f, 0.42f).edge(0.32f).wobble(1.00f)
                .halo(0f, 0f).trail(16, 3.2f, 0.70f, 0.30f, 1.0f).chargeSparks(0.50f).sparks(0.65f));

        put(KiTechniqueType.GALICK_GUN, b(KiVfxShape.BEAM)
                .mesh(4.2f, 0.27f, 0.34f).anchored().shell(0.70f).envelope(1.55f, 0.19f).core(0.62f, 0.87f)
                .bands(0.70f, 0.30f, 0.05f).tone(0.93f, 0.48f).edge(0.30f).wobble(0.85f)
                .halo(0f, 0f).trail(18, 2.4f, 0.72f, 0.30f, 1.1f).sparks(0.60f));

        put(KiTechniqueType.DEATH_BEAM, b(KiVfxShape.BEAM)
                .mesh(8.0f, 0.05f, 0.08f).anchored().shell(0.72f).envelope(2.1f, 0.12f).core(0.55f, 0.92f)
                .bands(0.65f, 0.28f, 0.04f).tone(0.98f, 0.65f).edge(0.26f).wobble(0.30f)
                .halo(0f, 0f).trail(40, 0.7f, 0.88f, 0.28f, 2.4f).sparks(0.15f));

        // DEATH_BALL: RIM sigue dando el filo crepitante (deathball_1-4), pero las referencias
        // TODAS muestran además un punto blanco-caliente claramente DESCENTRADO (el "sol" visto
        // desde un ángulo) — un RIM simétrico por sí solo no puede darlo. coreOffset() añade un
        // núcleo pequeño y desplazado hacia una esquina, sin tocar el fresnel del filo.
        put(KiTechniqueType.DEATH_BALL, b(KiVfxShape.SPHERE)
                .band(Band.RIM).shell(0.55f).envelope(1.60f, 0.22f).core(0.30f, 0.75f).coreOffset(-0.22f, 0.26f)
                .bands(0.58f, 0.24f, 0.05f).tone(0.75f, 0.35f).edge(0.34f).wobble(1.80f)
                .halo(2.4f, 0.20f).trail(0, 0f, 0f, 0f, 0f)
                .rays(8, 2.6f, 0.18f).chargeSparks(0.60f).sparks(1.40f));

        // SUPERNOVA: detailTexture LAVA (grietas, ver KiVfxRenderTypes) en vez de la genérica —
        // Supernova_5/6 muestran placas agrietadas con luz por dentro, no un ruido difuso.
        // coreOffset(): mismo "sol descentrado" que Death Ball, más sutil (núcleo más grande,
        // desplazamiento menor) porque aquí ya hay bandCore casi lleno debajo.
        put(KiTechniqueType.SUPERNOVA, b(KiVfxShape.SPHERE)
                .shell(0.90f).envelope(1.15f, 0.10f).core(0.55f, 0.85f).coreOffset(-0.16f, 0.20f)
                .bands(0.92f, 0.85f, 0.06f).tone(0.55f, 0.40f).edge(0.24f).wobble(0.45f)
                .halo(2.0f, 0.20f).trail(0, 0f, 0f, 0f, 0f)
                .detail(0.70f).lavaDetail().rays(6, 1.8f, 0.14f).sparks(0.25f));

        // Red de seguridad: un tipo nuevo sin entrada se dibuja como una bola estándar en vez de
        // reventar con un null.
        for (int i = 0; i < BY_TYPE.length; i++) {
            if (BY_TYPE[i] == null) BY_TYPE[i] = b(KiVfxShape.SPHERE).build();
        }
    }

    public static KiVfxProfile of(KiTechniqueType type) { return BY_TYPE[type.ordinal()]; }

    /** Inverso de {@link #of}: qué técnica tiene ESTE perfil (cada fila de la tabla es una
     *  instancia propia). Lo usa KiVfxTuning para los overrides por técnica, sin tener que pasar
     *  el tipo por cada firma del pipeline. null si el perfil no sale de la tabla. */
    @org.jetbrains.annotations.Nullable
    public static KiTechniqueType typeOf(KiVfxProfile p) {
        for (int i = 0; i < BY_TYPE.length; i++) {
            if (BY_TYPE[i] == p) return KiTechniqueType.values()[i];
        }
        return null;
    }

    public boolean hasTrail() { return trail.enabled(); }
    public boolean hasExplicitCore() {
        // DISK queda fuera aunque su Core tenga alpha>0 en la tabla (valor heredado de la ruta de
        // respaldo, irrelevante aquí): su canto YA es la lectura de núcleo — ver
        // KiVfxGeometry.core y el KI_ENERGY_RIM_U de KiVfxGeometry.
        return core.enabled() && shape != KiVfxShape.DISK;
    }
    public boolean hasRays() { return rays.enabled(); }

    /** ¿Esta forma tiene un eje de vuelo real (BEAM, HELIX)? El valor que espera el uniform
     *  ZenkaiAxial — ver "BUG DE CÁMARA" en ki_energy.fsh: SURFACE necesita ignorar la componente
     *  de la vista A LO LARGO del eje SOLO en formas alargadas. */
    public boolean axial() { return shape == KiVfxShape.BEAM || shape == KiVfxShape.HELIX; }

    /** Radio característico REAL (en bloques de mundo) del cuerpo ya escalado a {@code size} — NO
     *  el {@code meshRadius} normalizado (grosor de referencia 1). Sirve para medir qué tan cerca
     *  está la cámara RESPECTO AL PROPIO TAMAÑO de la técnica (ver KiVfxRenderTypes.proximity). */
    public float worldRadius(float size) {
        return (shape == KiVfxShape.SPHERE || shape == KiVfxShape.DISK) ? size * 0.5f : size * shell.meshRadius();
    }

    private static final float WRAP_CAMERA_FP_DAMPEN = 0.35f;

    /** Dampen extra, encima del ajuste general de primera persona, para la vista de la PROPIA
     *  técnica. Reutiliza {@link #backfaceCull}: ese campo marca las formas SURFACE cuya cáscara
     *  puede envolver la cámara (hoy EXPLOSION; BARRIER se oculta entera en tu primera persona,
     *  ver KiVfxChargeRenderer.hidesFullyInFirstPerson). */
    public float firstPersonOpacity(float baseFrac) {
        return backfaceCull ? baseFrac * WRAP_CAMERA_FP_DAMPEN : baseFrac;
    }

    // ── Constructor fluido ──────────────────────────────────────────────────

    private static void put(KiTechniqueType type, Builder b) { BY_TYPE[type.ordinal()] = b.build(); }
    private static Builder b(KiVfxShape shape) { return new Builder(shape); }

    /** Qué textura de detalle de superficie usa un {@link Shell} (ver KiVfxRenderTypes) — la
     *  mayoría de técnicas ni siquiera activan la capa ({@code detailStrength} 0), así que este
     *  campo solo importa para las pocas que sí. LAVA: placas agrietadas con luz por dentro
     *  (Supernova). */
    public enum DetailTexture { STANDARD, LAVA }

    /**
     * MULTIPLICADORES GLOBALES DE SILUETA (2026-09-24). La tabla de abajo se calibró SOLO contra
     * sí misma (comparando una técnica con otra), nunca contra una captura real en juego. El
     * usuario mandó un vídeo tras la reconstrucción 2026-09-23: el resultado era una masa de
     * círculos translúcidos enormes sin estructura — "que no parezca luz difusa, que parezca
     * energía" fue el pedido explícito, con permiso expreso de usar bloom real y lo que hiciera
     * falta para acercarse a las referencias.
     * En vez de retocar 16 filas a mano (con el riesgo de que dos números queden inconsistentes
     * entre sí sin que se note en el código), se aplica un factor ÚNICO aquí, documentado una
     * sola vez:
     *  - El HALO deja de ser el elemento dominante — su trabajo ahora es un acento suave; el
     *    resplandor real lo pone {@link KiVfxBloomPipeline} (bloom de pantalla completa,
     *    difuminado de verdad, no un billboard sobredimensionado).
     *  - La ENVOLVENTE se aprieta — sigue dando el límite gaseoso en 3D, pero deja de competir en
     *    tamaño con el propio cuerpo.
     *  - El EDGE FADE se endurece — la silueta se DISUELVE menos y se LEE más, que es la
     *    diferencia entre "energía estructurada" y "una mancha con los bordes borrosos".
     * Retunear estos cuatro números es el primer sitio a mirar si el resultado en juego sigue
     * sin cuadrar, antes que tocar ninguna fila individual.
     */
    private static final float HALO_SCALE_MUL = 0.55f;
    private static final float HALO_ALPHA_MUL = 0.70f;
    private static final float ENVELOPE_ALPHA_MUL = 0.65f;
    private static final float EDGE_FADE_MUL = 0.55f;

    /** Los defaults son los de BLAST. Cada tipo declara solo lo que lo diferencia. */
    private static final class Builder {
        private final KiVfxShape shape;
        private Band band = Band.SURFACE;
        private float meshLength = 1.0f, meshRadius = 0.5f, headScale = 0f;
        private boolean anchorTip = false;
        private float shellAlpha = 0.50f;
        private boolean backfaceCull = false;
        private float envelopeScale = 1.35f, envelopeAlpha = 0.15f;
        private float coreScale = 0.62f, coreAlpha = 0.85f, coreOffsetX = 0f, coreOffsetY = 0f;
        private float bandCore = 0.75f, bandBorder = 0.30f, bandOutline = 0.05f;
        private float coreWhite = 0.90f, outlineDark = 0.55f, edgeFade = 0.30f, wobble = 1.0f;
        private float haloScale = 2.2f, haloAlpha = 0.24f;
        private int trailPoints = 0;
        private float trailWidth = 0f, trailAlpha = 0.70f, trailInnerMul = 0.30f, trailScroll = 1.0f;
        private boolean helixTrail = false;
        private float detailStrength = 0f;
        private DetailTexture detailTexture = DetailTexture.STANDARD;
        private float sparkRate = 0.35f;
        private float chargeSparkRate = 0f;
        private int rayCount = 0;
        private float rayLength = 0f, rayAlpha = 0f;

        Builder(KiVfxShape shape) { this.shape = shape; }

        Builder band(Band v) { this.band = v; return this; }
        Builder mesh(float length, float radius, float head) {
            meshLength = length; meshRadius = radius; headScale = head; return this;
        }
        Builder anchored() { this.anchorTip = true; return this; }
        Builder shell(float v) { this.shellAlpha = v; return this; }
        Builder backfaceCull() { this.backfaceCull = true; return this; }
        Builder envelope(float scale, float alpha) { envelopeScale = scale; envelopeAlpha = alpha; return this; }
        Builder core(float scale, float alpha) { coreScale = scale; coreAlpha = alpha; return this; }
        /** Ver KiVfxProfile.Core: desplaza el núcleo respecto al centro, como fracción del
         *  tamaño de la técnica. Solo tiene efecto si se llama DESPUÉS de {@link #core}. */
        Builder coreOffset(float ox, float oy) { coreOffsetX = ox; coreOffsetY = oy; return this; }
        Builder bands(float c, float bo, float o) { bandCore = c; bandBorder = bo; bandOutline = o; return this; }
        Builder tone(float white, float dark) { coreWhite = white; outlineDark = dark; return this; }
        Builder edge(float v) { this.edgeFade = v; return this; }
        Builder wobble(float v) { this.wobble = v; return this; }
        Builder halo(float scale, float alpha) { haloScale = scale; haloAlpha = alpha; return this; }
        Builder trail(int points, float width, float alpha, float innerMul, float scroll) {
            trailPoints = points; trailWidth = width; trailAlpha = alpha;
            trailInnerMul = innerMul; trailScroll = scroll;
            return this;
        }
        Builder helixTrail() { this.helixTrail = true; return this; }
        Builder detail(float v) { this.detailStrength = v; return this; }
        Builder lavaDetail() { this.detailTexture = DetailTexture.LAVA; return this; }
        Builder sparks(float v) { this.sparkRate = v; return this; }
        Builder chargeSparks(float v) { this.chargeSparkRate = v; return this; }
        Builder rays(int count, float length, float alpha) {
            rayCount = count; rayLength = length; rayAlpha = alpha; return this;
        }

        KiVfxProfile build() {
            return new KiVfxProfile(shape,
                    new Shell(meshLength, meshRadius, headScale, shellAlpha, band,
                            bandCore, bandBorder, bandOutline, coreWhite, outlineDark,
                            edgeFade * EDGE_FADE_MUL, wobble, detailStrength, detailTexture),
                    new Core(coreScale, coreAlpha, coreOffsetX, coreOffsetY),
                    new Envelope(envelopeScale, envelopeAlpha * ENVELOPE_ALPHA_MUL),
                    new Halo(haloScale * HALO_SCALE_MUL, haloAlpha * HALO_ALPHA_MUL),
                    new Ribbon(trailPoints, trailWidth, trailAlpha, trailInnerMul, trailScroll),
                    new Rays(rayCount, rayLength, rayAlpha),
                    new Particles(sparkRate, chargeSparkRate),
                    anchorTip, backfaceCull, helixTrail);
        }
    }
}
