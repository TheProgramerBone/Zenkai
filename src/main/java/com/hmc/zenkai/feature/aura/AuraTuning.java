package com.hmc.zenkai.feature.aura;

/**
 * TODAS las constantes del sistema de aura, y el ÚNICO sitio donde viven.
 * Los números NO son inventados: salen de una calibración hecha antes de escribir esto
 * (generador procedural de siluetas + simulación del pipeline de render + réplica exacta
 * de la cadena PL). Cambiar uno cambia el aspecto del mod, así que cada bloque
 * lleva de dónde viene.
 * REGLA QUE ESTE ARCHIVO SOSTIENE: un AuraModifier (raza o forma) puede tocar la FORMA
 * del aura, nunca su PODER. Por eso los coeficientes de size/alpha/core/wisps/sparks/
 * ground viven aquí y solo consumen AuraState — no hay ningún camino por el que un
 * datapack pueda alterarlos.
 */
public final class AuraTuning {
    private AuraTuning() {}

    // ── Perfil base C_v2 ─────────────────────────────────────────────────────
    // Dirección artística congelada: masa orgánica continua con puntas agresivas que
    // nacen DENTRO de la masa. mass 0.72 es el punto elegido tras el barrido
    // 0.55/0.63/0.72/0.82: por debajo se lee como "muchas llamas independientes",
    // por encima se traga al jugador.
    public static final float C_MASS    = 0.72f;
    public static final float C_SPIKE   = 0.52f;
    public static final float C_TURB    = 0.12f;
    public static final float C_SPREAD  = 0.03f;
    public static final float C_HEIGHT  = 1.18f;
    public static final float C_DENSITY = 0.90f;

    // ── Modulación de la forma por el estado ─────────────────────────────────
    // mass sube con el refinamiento y baja con el caos: dominar el ki lo COMPACTA.
    public static final float MASS_BASE      = 0.82f;
    public static final float MASS_PER_REFINE = 0.26f;
    public static final float MASS_PER_CHAOS  = -0.16f;

    public static final float SPIKE_PER_PRESENCE = 0.22f;

    // turbulencia y dispersión: la parte REACTIVA (lo que multiplica *Gain).
    public static final float TURB_PER_CHAOS       = 0.38f;
    public static final float TURB_PER_INSTABILITY = 0.18f;
    public static final float TURB_PER_KAIOKEN     = 0.30f;

    public static final float SPREAD_PER_CHAOS       = 0.42f;
    public static final float SPREAD_PER_INSTABILITY = 0.14f;
    public static final float SPREAD_PER_KAIOKEN     = 0.26f;

    public static final float HEIGHT_BASE        = 0.86f;
    public static final float HEIGHT_PER_RELEASE = 0.28f;

    public static final float DENSITY_BASE         = 0.78f;
    public static final float DENSITY_PER_PRESENCE = 0.67f;

    // ── Canales de PODER. Ningún AuraModifier los toca. ──────────────────────
    //
    // SIZE_BASE es el aura de un personaje recién creado liberando al 100%, y es el
    // MÍNIMO del sistema: por debajo de esto no baja nadie. Se fijó mirando el juego,
    // no calculándolo.
    //
    // La presencia MULTIPLICA sobre ese mínimo en vez de sumar sobre una base alta.
    // Antes el reparto era este:
    //     presencia (PL)  0.86 -> 1.14   solo  32% de variación
    //     liberación      0.35 -> 1.00        186%
    //     energía         0.58 -> 1.00         72%
    // o sea que lo que se veía crecer NO era el Power Level sino el powerPercent, y
    // el PL —que debería mandar— era el factor que menos pesaba de los tres. Con la
    // ganancia multiplicativa el PL pasa a valer x1.9 de punta a punta.
    public static final float SIZE_BASE          = 0.86f;
    public static final float SIZE_PER_PRESENCE  = 0.90f;
    public static final float SIZE_RELEASE_BASE  = 0.35f;
    public static final float SIZE_PER_RELEASE   = 0.65f;
    public static final float SIZE_ENERGY_BASE   = 0.58f;
    public static final float SIZE_PER_ENERGY    = 0.42f;
    public static final float SIZE_PER_TURBO     = 0.22f;
    public static final float SIZE_PER_KAIOKEN   = 0.10f;   // deliberadamente poco

    /**
     * Suelo del tamaño mientras el aura esté activa. Coincide con SIZE_BASE: el aura
     * más pequeña posible es la de un recién creado liberando al 100%.
         * CONSECUENCIA ACEPTADA: la supresión ya no reduce el TAMAÑO por debajo de este
     * suelo. Un endgame escondiendo su ki al 5% se ve exactamente igual de grande que
     * un novato — que es justamente lo que se quiere de una supresión creíble. Lo que
     * sí sigue delatándole son los canales que dependen de presence y no de size:
     * menos wisps, menos chispas y menos densidad de los que tendría al descubierto.
     */
    public static final float SIZE_MIN = 0.86f;

    public static final float ALPHA_BASE       = 0.72f;
    public static final float ALPHA_PER_ENERGY = 0.46f;
    public static final float ALPHA_PER_TURBO  = 0.15f;

    // 0.35 es el punto de equilibrio del núcleo blanco: por debajo el aura es plana,
    // por encima el color se lava a crema y el amarillo deja de leerse como amarillo.
    public static final float CORE_MUL        = 0.35f;
    public static final float CORE_BASE       = 0.72f;
    public static final float CORE_PER_ENERGY = 0.56f;
    public static final float CORE_PER_TURBO  = 0.30f;

    public static final float WISPS_BASE       = 6f;
    public static final float WISPS_PER_PRESENCE = 18f;
    public static final float SPARKS_PER_SEC   = 14f;
    public static final float SPARKS_PER_KAIOKEN = 2.5f;

    // ── Estado energético ────────────────────────────────────────────────────
    // energy nunca llega a 0: una forma activa siempre tiene aura perceptible.
    public static final float ENERGY_FLOOR   = 0.15f;
    public static final float ENERGY_PER_KI  = 0.85f;
    /** Por debajo de este % de ki el aura empieza a volverse inestable. */
    public static final float INSTABILITY_KNEE = 0.25f;

    // chaos = tension × (1 − refine), atenuado por presencia. Sin la atenuación, un
    // personaje recién creado (powerPercent 50 = su techo exacto con ki_control 0)
    // salía con chaos 1.00, o sea máxima inestabilidad visual el primer minuto de
    // partida. La atenuación lo deja en ~0.41 sin tocar una línea de balance: la
    // energía que no controlas solo se ve dispersarse si hay energía suficiente.
    public static final float CHAOS_VIS_BASE         = 0.35f;
    public static final float CHAOS_VIS_PER_PRESENCE = 0.65f;

    // ── Kaioken: firma TEMPORAL. No crece, late. ─────────────────────────────
    public static final float PULSE_HZ_BASE     = 0.6f;
    public static final float PULSE_HZ_PER_KI   = 2.4f;
    public static final float PULSE_AMP_BASE    = 0.04f;
    public static final float PULSE_AMP_PER_KI  = 0.18f;

    // ── Cadencia de textura ──────────────────────────────────────────────────
    // No es un parámetro del perfil: se DERIVA de turbulence. Cuanto más
    // descontrolada está el aura, más rápido pasa de frame. Así un SSJ va más
    // frenético que una forma tranquila sin que nadie lo configure, y la relación
    // "inestable = viva" queda establecida en un solo sitio.
    // Los límites existen porque un Majin descontrolado con Kaioken x20 llega a
    // turbulence ~1.0, y sin tope la hoja se animaría a 4 fps aparentes.
    //
    // PRESENCE TAMBIÉN ACELERA, y no es un extra: atarla solo a turbulence dejaba al
    // jugador MÁS poderoso con la animación MÁS lenta del juego. Un endgame maestro
    // tiene chaos = tension × (1 − refine) = 0, así que su turbulence se queda en el
    // mínimo y el aura se veía plana justo donde debía impresionar. Ahora hay dos
    // motivos para que la energía se mueva rápido: estar descontrolado, o tener
    // muchísima.
    public static final float FRAME_TICKS_BASE = 2.2f;
    public static final float FRAME_TICKS_PER_TURB = 0.55f;
    public static final float FRAME_TICKS_PER_PRESENCE = 0.40f;
    public static final float FRAME_TICKS_MIN = 1.0f;
    public static final float FRAME_TICKS_MAX = 3.0f;

    // ── Giro del anillo de suelo ─────────────────────────────────────────────
    // El anillo gira: sin esto se lee como un decal pegado al terreno. La velocidad
    // sube con la presencia por el mismo motivo que la cadencia.
    public static final float GROUND_SPIN_BASE = 14f;    // grados/segundo
    public static final float GROUND_SPIN_PER_PRESENCE = 46f;

    /** Luminancia por debajo de la cual NO se dibuja núcleo blanco: en un aura
     *  oscura el núcleo la convertiría en gris. Vive aquí y no en el renderer
     *  porque la decisión la toma AuraSkirts.plan al resolver los colores. */
    public static final float CORE_MIN_LUM = 0.25f;

    /** Desplazamiento del núcleo hacia la cámara, en bloques. Con sortOnUpload=true
     *  los quads del mismo RenderType se ordenan por profundidad, así que masa y
     *  núcleo del MISMO plano (mismo z) quedarían en orden indeterminado. Este
     *  offset los separa lo justo para que el sort haga lo correcto. */
    public static final float CORE_Z_OFFSET = 0.002f;

    /** Escala y alpha de la capa envolvente (kaioken sobre forma). */
    public static final float OUTER_SCALE_MUL = 1.26f;
    public static final float OUTER_ALPHA_MUL = 0.85f;

    // ── Atenuación del sector frontal ────────────────────────────────────────
    // Los planos entre cámara y jugador se atenúan para que el jugador se lea
    // siempre. Sin esto el aura se lo come a corta distancia.
    public static final float FRONT_FADE_MIN  = 0.30f;
    public static final float FRONT_DOT_START = 0.40f;
    public static final float FRONT_DOT_FULL  = 0.85f;

    // ── Layout de la hoja de llamas ──────────────────────────────────────────
    // UNA sola textura por frame, 2 columnas x 4 filas:
    //   filas 0-1  MASA   (se tinta)
    //   filas 2-3  NÚCLEO (blanco, sin tintar)
    // Van juntas para que masa y núcleo compartan RenderType: el memoize es por
    // textura, así que separarlas en dos PNG duplicaría los draw calls.
    public static final int SHEET_FRAMES = 4;
    public static final float CELL_U = 0.5f;
    public static final float CELL_V = 0.25f;
    /** Fila donde empiezan los cuadrantes de núcleo. */
    public static final int CORE_ROW_OFFSET = 2;
    /** Inset de UV para que el filtrado bilineal no sangre entre celdas. */
    public static final float UV_INSET = 0.0015f;

    // ── Normalización del PL ─────────────────────────────────────────────────
    /**
     * ÚNICO número de referencia del sistema: TP total que se espera que invierta un
     * jugador de endgame. PROVISIONAL — pendiente de verificar contra la progresión
     * real de TP. No hay que tocarlo al añadir formas: AuraCeiling recorre el registro
     * entero, así que una transformación nueva sube el techo sola.
     * TODO: mover a CommonConfig cuando se confirme el valor.
     */
    public static final double REFERENCE_TP = 5_000_000d;

    /** Suelo de la escala: PL de un personaje recién creado. */
    public static final long PL_FLOOR_FALLBACK = 231L;
    /** Techo de emergencia si RaceStatTable todavía no ha cargado. */
    public static final long PL_CEIL_FALLBACK = 4_534_321L;

    // ── Rangos válidos. AuraProfile clampa a esto SIEMPRE. ───────────────────
    public static final float MIN_MASS = 0.05f, MAX_MASS = 1.0f;
    public static final float MIN_SPIKE = 0.0f, MAX_SPIKE = 1.0f;
    public static final float MIN_TURB = 0.0f, MAX_TURB = 1.0f;
    public static final float MIN_SPREAD = 0.0f, MAX_SPREAD = 0.9f;
    public static final float MIN_HEIGHT = 0.5f, MAX_HEIGHT = 2.0f;
    public static final float MIN_DENSITY = 0.4f, MAX_DENSITY = 2.0f;

    /** Tope de los offsets de un AuraModifier. Una forma puede empujar ±0.25;
     *  una raza se queda en ±0.10 por convención, pero el límite duro es este. */
    public static final float MAX_OFFSET = 0.5f;
    public static final float MIN_GAIN = 0.25f, MAX_GAIN = 3.0f;

    /** Tolerancia de neutralidad entre firmas raciales, en masa visual.
     *  El ruido inter-semilla del generador de siluetas es 3.9%: perseguir décimas
     *  por debajo de eso es falsa precisión. */
    public static final float NEUTRALITY_TOLERANCE = 0.05f;

    // ── util ─────────────────────────────────────────────────────────────────
    public static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (Math.min(v, hi));
    }

    public static float clamp01(float v) { return clamp(v, 0f, 1f); }

    /** Ticks por frame de la hoja: acelera con el descontrol Y con la presencia. */
    public static float frameTicks(float turbulence, float presence) {
        return clamp(FRAME_TICKS_BASE * (1f
                        - FRAME_TICKS_PER_TURB * clamp01(turbulence)
                        - FRAME_TICKS_PER_PRESENCE * clamp01(presence)),
                FRAME_TICKS_MIN, FRAME_TICKS_MAX);
    }

    /** Grados por segundo que gira el anillo de energía de suelo. */
    public static float groundSpin(float presence) {
        return GROUND_SPIN_BASE + GROUND_SPIN_PER_PRESENCE * clamp01(presence);
    }

    /** Luminancia percibida de un RGB empaquetado. */
    public static float luminance(int rgb) {
        return 0.299f * (((rgb >> 16) & 0xFF) / 255f)
                + 0.587f * (((rgb >> 8) & 0xFF) / 255f)
                + 0.114f * ((rgb & 0xFF) / 255f);
    }

    public static float smoothstep(float v) {
        float t = clamp01(v);
        return t * t * (3f - 2f * t);
    }
}