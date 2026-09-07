package com.hmc.zenkai.feature.technique;

import java.util.Locale;

/**
 * Efecto añadido de una técnica de ki. UNO SOLO por técnica, nunca combinables.
 * POR QUÉ UN ENUM Y NO CASILLAS SUELTAS
 * -------------------------------------
 * Antes era un boolean `explosive`. Con casillas independientes el editor se convierte en una
 * hoja de cálculo, las combinaciones hay que balancearlas una a una, y una técnica "that has all"
 * no tiene identidad. Con un selector único cada técnica dice una cosa.
 * QUÉ HACE HOY CADA UNO
 * ----------------------
 * EXPLOSIVE:   SOLO romper bloques en el radio de la explosión. El daño en ÁREA ya no depende
 *              de la marca: cualquier técnica que impacta lo reparte según el aoeFactor de su tipo.
 *              Por eso el recargo baja de ×1.5 a ×1.15 — destruir terreno no vale lo que valía
 *              "área + terreno". Consecuencia: con la gamerule de griefing apagada este efecto
 *              NO HACE NADA, y por eso el editor debe deshabilitarlo en ese caso en vez de
 *              cobrarlo (ver entrega 2).
 * PIERCING:    atraviesa a la primera entidad golpeada y sigue volando en vez de detonar y
 *              desaparecer. DISK ya hace exactamente esto sin marcar nada (es su identidad de
 *              tipo, ver KiTechniqueType.allowsEffect); este efecto pone el MISMO mecanismo
 *              (KiProjectileEntity.pierced) a disposición de los tipos que por defecto SÍ paran
 *              en el primer impacto.
 * HOMING:      corrige el rumbo un poco cada tick hacia el objetivo fijado con Ki Sense
 *              (lock-on, ver SenseServerState). Sin sense activo o sin lock, vuela recto como
 *              cualquier otro — está ATADO a esa habilidad, no es un misil autónomo.
 * LINGERING:   deja una zona en el punto de impacto (KiLingeringZoneEntity) que sigue haciendo
 *              daño unos segundos antes de apagarse sola.
 * FRAGMENTATION: al impactar suelta 3-4 bolas pequeñas adicionales, sin efecto (para que la
 *              fragmentación no se fragmente a sí misma sin límite).
 * AÑADIR UN EFECTO = una constante aquí, su clave de lang, su color de marco y su rama de
 * comportamiento. No se declaran efectos sin implementar: una opción que no hace nada en el
 * editor es peor que no tenerla.
 * QUÉ TIPO ADMITE QUÉ EFECTO vive en KiTechniqueType.allowsEffect, no aquí: es el tipo quien
 * conoce su propia identidad (un disco que explota deja de ser un disco, una barrera no
 * impacta nada que perforar/fragmentar/corregir).
 * El ORDINAL viaja en NBT y en el packet: las nuevas van AL FINAL.
 */
public enum TechniqueEffect {

    NONE(1.00, 0),

    EXPLOSIVE(1.15, 0xFFE08A2B),

    PIERCING(1.10, 0xFF6FD6E8),

    HOMING(1.20, 0xFFB35FE0),

    LINGERING(1.20, 0xFFE0432B),

    FRAGMENTATION(1.15, 0xFFE0D02B);

    private final double costMult;
    private final int borderRgb;

    TechniqueEffect(double costMult, int borderRgb) {
        this.costMult = costMult;
        this.borderRgb = borderRgb;
    }

    /** Multiplicador sobre el coste de ki del disparo. */
    public double costMult() { return costMult; }

    /** Color del marco de la casilla en el HUD y en la barra de asignación. 0 = sin marco.
     *  Vive aquí y no en la GUI porque es identidad del efecto, no decoración de una pantalla. */
    public int borderRgb() { return borderRgb; }

    /**
     * Factor con el que {@link #panelRgb()} oscurece {@link #borderRgb()}.
     * Los colores de marco están pensados para verse SOBRE FONDO OSCURO (el HUD, la casilla de
     * la barra), y sobre el beige del panel son ilegibles: medidos contra ZenkaiPalette.BEIGE
     * dan ratios de contraste de 1.0 a 2.6 — el cian de PIERCING y el amarillo de FRAGMENTATION
     * literalmente 1.0, o sea invisibles. A 0.45 los cinco quedan en 4.2..7.5, dentro de la
     * banda que ya ocupa la familia *_ON_PANEL de ZenkaiPalette (3.7..7.0), así que se leen
     * igual de bien que cualquier otro texto del panel sin dejar de ser reconociblemente el
     * mismo color del efecto.
     * Se deriva en vez de listar cinco hexadecimales nuevos a mano para que el tono no se pueda
     * desincronizar del marco si alguien retoca borderRgb.
     */
    private static final double PANEL_DARKEN = 0.45;

    /**
     * El color del efecto ADAPTADO al beige del panel (editor de técnicas, listas). Mismo tono
     * que {@link #borderRgb()}, oscurecido — ver PANEL_DARKEN para el porqué y los números.
     * NONE devuelve 0 igual que borderRgb: no es un efecto, no tiene color propio.
     */
    public int panelRgb() {
        if (borderRgb == 0) return 0;
        int r = (int) (((borderRgb >> 16) & 0xFF) * PANEL_DARKEN);
        int g = (int) (((borderRgb >> 8) & 0xFF) * PANEL_DARKEN);
        int b = (int) ((borderRgb & 0xFF) * PANEL_DARKEN);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    public String langKey() {
        return "technique.zenkai.effect." + name().toLowerCase(Locale.ROOT);
    }

    /** Clave de traducción de la descripción corta (tooltip del selector en el editor). */
    public String descKey() { return langKey() + ".desc"; }

    /** Nunca falla: un ordinal fuera de rango (NBT viejo, packet manipulado) cae a NONE. */
    public static TechniqueEffect byOrdinal(int i) {
        TechniqueEffect[] all = values();
        return (i < 0 || i >= all.length) ? NONE : all[i];
    }
}
