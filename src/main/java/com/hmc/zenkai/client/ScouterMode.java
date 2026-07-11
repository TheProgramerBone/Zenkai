package com.hmc.zenkai.client;

/**
 * Modos del scouter (F4 cicla en orden). El panel muestra el título del modo como feedback
 * del ciclo (sin actionbar).
 */
public enum ScouterMode {
    OFF,
    /** PL de lo que tienes en la mira + etiqueta DÉBIL/FORMIDABLE/AMENAZA. */
    POWER,
    /** Busca la entidad con MÁS PL en rango (solo jugadores con raza y mobs con stats). */
    STRONGEST,
    /** Esfera del dragón más cercana (requiere la mejora de radar en el ítem). */
    RADAR;

    private static final ScouterMode[] VALUES = values();

    public ScouterMode next() {
        return VALUES[(ordinal() + 1) % VALUES.length];
    }

    /** Clave de traducción del título del modo (no aplica a OFF). */
    public String titleKey() {
        return "scouter.zenkai.mode." + name().toLowerCase();
    }
}