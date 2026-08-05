package com.hmc.zenkai.client.overlay;

import com.hmc.zenkai.feature.sense.ScouterUpgrade;
import org.jetbrains.annotations.Nullable;

/**
 * Modos del scouter (F4 cicla adelante, Shift+F4 atrás). El panel muestra el título del modo
 * como feedback del ciclo (sin actionbar).
 *
 * Cada modo declara AQUÍ qué mejora lo desbloquea. Es el único sitio donde vive esa relación:
 * el cliente la usa para no mandar escaneos que el servidor va a rechazar y para pintar
 * "UPGRADE UNAVAILABLE", y el servidor la usa para rechazarlos de verdad.
 *
 * Ciclar hasta un modo bloqueado SÍ está permitido: enseña que existe y qué te falta. Un ciclo
 * que se saltara los modos bloqueados escondería el contenido en vez de anunciarlo.
 */
public enum ScouterMode {
    OFF(null),
    /** PL de lo que tienes en la mira + etiqueta DÉBIL/FORMIDABLE/AMENAZA. Siempre disponible. */
    POWER(null),
    /**
     * Desglose del PL del objetivo: melee, defensa y poder de ki. Van justo detrás de POWER
     * porque son literalmente sus sumandos — con los pesos a 1.0, melee+defensa+kiPower ES
     * el PL, así que este modo explica el número del anterior.
     */
    ATTRIBUTES(ScouterUpgrade.ANALYZER),
    /** Busca la entidad con MÁS PL en rango (solo jugadores con raza y mobs con stats). */
    STRONGEST(ScouterUpgrade.AREA_SCANNER),
    /** Esfera del dragón más cercana. */
    RADAR(ScouterUpgrade.DRAGON_RADAR);

    private static final ScouterMode[] VALUES = values();

    @Nullable private final ScouterUpgrade required;

    ScouterMode(@Nullable ScouterUpgrade required) {
        this.required = required;
    }

    /** Mejora que desbloquea este modo, o null si no necesita ninguna. */
    @Nullable public ScouterUpgrade required() { return required; }

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