package com.hmc.zenkai.client.overlay;

/**
 * Modos del scouter (F4 cicla en orden). El panel muestra el título del modo como feedback
 * del ciclo (sin actionbar).
 */
public enum ScouterMode {
    OFF,
    /** PL de lo que tienes en la mira + etiqueta DÉBIL/FORMIDABLE/AMENAZA. */
    POWER,
    /**
     * Desglose del PL del objetivo: melee, defensa y poder de ki. Van justo detrás de POWER
     * porque son literalmente sus sumandos — con los pesos a 1.0, melee+defensa+kiPower ES
     * el PL, así que este modo explica el número del anterior.
     */
    ATTRIBUTES,
    /** Busca la entidad con MÁS PL en rango (solo jugadores con raza y mobs con stats). */
    STRONGEST,
    /** Esfera del dragón más cercana (requiere la mejora de radar en el ítem). */
    RADAR;

    private static final ScouterMode[] VALUES = values();

    public ScouterMode next() {
        return VALUES[(ordinal() + 1) % VALUES.length];
    }

    /** Modo anterior (Shift+F4). OFF sigue dentro del ciclo en las dos direcciones. */
    public ScouterMode prev() {
        return VALUES[(ordinal() - 1 + VALUES.length) % VALUES.length];
    }

    /** Clave de traducción del título del modo (no aplica a OFF). */
    public String titleKey() {
        return "scouter.zenkai.mode." + name().toLowerCase();
    }
}