package com.hmc.zenkai.feature.technique;

/**
 * Registro de sets de animación de técnica de ki. UN SET ES UN CONCEPTO ÚNICO: los tres clips
 * de PAL y el punto del que sale la técnica son la misma decisión, no dos.
 * POR QUÉ VIVE EN COMÚN Y NO EN EL CLIENTE
 * ----------------------------------------
 * Las rutas de los clips son de PAL y por tanto solo cliente, pero el ORIGEN lo necesita el
 * servidor: KiFirePacket.execute calcula ahí el spawn del proyectil. Por eso el registro baja
 * aquí y TechniqueAnimSets (cliente) se queda solo con la construcción de ResourceLocations.
 * POR QUÉ NO ES DATAPACK
 * ----------------------
 * Un set existe si y solo si existen sus clips en assets/zenkai/player_animations/, que los
 * modelas tú en Blockbench. Un datapack podría declarar el set 5 sin animación que lo respalde
 * y el fallo aparecería en juego, no al cargar. Además PAL no permite consultar el registro
 * (por eso el conteo se lleva a mano), así que validar tampoco sería posible.
 * AÑADIR UN SET = añadir una constante aquí. Es el único sitio que tocar; el conteo, el clamp
 * y las tres rutas de cliente salen de esto.
 * El ORDEN IMPORTA: el número de set (1..N) es ordinal()+1 y viaja en ActionState.visual y en
 * el NBT de las técnicas guardadas. Los sets nuevos van AL FINAL.
 */
public enum TechniqueAnimSet {
    /** Brazo derecho al frente. */
    SET_1(TechniquePosition.RIGHT_HAND),
    /** Brazo izquierdo al frente: el reflejo del 1, no una pose distinta. */
    SET_2(TechniquePosition.LEFT_HAND),
    /** Final Flash: brazos abiertos a los lados que se juntan al disparar. */
    SET_3(TechniquePosition.BOTH_HANDS),
    /** Rayo mortal: un brazo al frente, quieto. */
    SET_4(TechniquePosition.RIGHT_HAND),
    /** Kamehameha: manos a la cadera derecha, empuje frontal. */
    SET_5(TechniquePosition.BOTH_HANDS),
    /** Death Ball: brazo derecho recto arriba, esfera sobre la palma. */
    SET_6(TechniquePosition.RIGHT_HAND),
    /** Doble palma frontal: las dos manos abiertas al frente. */
    SET_7(TechniquePosition.BOTH_HANDS),
    /** Galick Gun: manos juntas al costado, a la altura de la cintura. */
    SET_8(TechniquePosition.BOTH_HANDS),
    /** Makankosappo. La carga tiene DOS tiempos —postura tensa y luego los dedos subiendo a
     *  la frente— y por eso su clip dura 20 ticks en vez de los 14-18 del resto. Único set que
     *  ancla en la cabeza: la esfera vive en la frente desde el primer frame hasta el disparo,
     *  sin cambiar de sitio a mitad de la secuencia. */
    SET_9(TechniquePosition.FOREHEAD);

    /**
     * BARRIER no tiene set: su animación es única y en el estado se marca con visual == 0.
     * Sale de ambas manos, igual que SET_2, y a propósito NO como una excepción escondida en
     * el enum — es una constante con nombre para que se vea que es una regla aparte.
     */
    public static final TechniquePosition BARRIER_POSITION = TechniquePosition.BOTH_HANDS;

    private final TechniquePosition position;

    TechniqueAnimSet(TechniquePosition position) {
        this.position = position;
    }

    /** De dónde sale la técnica con este set. Ya no lo elige el jugador: lo dicta la animación. */
    public TechniquePosition position() { return position; }

    /** Número de set tal como viaja por la red y se guarda (1..N). */
    public int number() { return ordinal() + 1; }

    /** Nombre visible del set. Un set no es un número: son tres clips y un origen, y en el
     *  editor "Animation: 4" no le dice nada a nadie. La clave se deriva del número para que
     *  añadir un set siga siendo añadir UNA constante aquí y UNA línea en el lang. */
    public String langKey() { return "technique.zenkai.anim_set." + number(); }

    public static int count() { return values().length; }

    /** Cualquier número guardado cae dentro de lo que existe hoy. Un set inexistente anima
     *  como el 1: que una técnica no se anime es peor que animarse distinto. */
    public static int clamp(int set) {
        return (set < 1 || set > values().length) ? 1 : set;
    }

    public static TechniqueAnimSet byNumber(int set) {
        return values()[clamp(set) - 1];
    }

    public static TechniquePosition positionOf(int set) {
        return byNumber(set).position();
    }
}