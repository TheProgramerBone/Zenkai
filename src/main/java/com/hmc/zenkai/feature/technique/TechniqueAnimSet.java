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
    /** Un brazo al frente. */
    SET_1(TechniquePosition.RIGHT_HAND),
    /** Ambas manos al costado, tipo Kamehameha. */
    SET_2(TechniquePosition.BOTH_HANDS);

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