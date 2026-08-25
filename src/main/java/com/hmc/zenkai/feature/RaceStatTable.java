package com.hmc.zenkai.feature;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Cuánto rinde UN punto de atributo, por raza y estilo.
 * Columnas de RENDIMIENTO: MELEE=STR · DEFENSE=DEX · HEALTH y STAMINA=CON · KI_DMG=WIL ·
 * KI_RES=SPI. HEALTH y STAMINA son independientes: Majin es un tanque (52 HP/punto) sin ser un
 * pozo de estamina, y Namekian al revés. DEX ya NO alimenta la velocidad (eso vive en las
 * habilidades run/fly), así que subirla es puramente defensivo.
 * Columnas de COSTE: KI_COST y STAM_COST son multiplicadores sobre los factores globales.
 * Existen porque el número de usos por barra llena es (pool / poder) / factor, y ese ratio
 * varía 2.8× entre un saiyan warrior y un namekiano martial artist: un único factor global o
 * dejaba al saiyan sin poder lanzar su definitiva, o le regalaba ki infinito al namekiano.
 * 1.0 = neutro, <1.0 = más barato, >1.0 = más caro.
 * ═══ LOS DATOS VIVEN EN EL DATAPACK, Y SOLO AHÍ ═══
 * Esta clase YA NO lleva una tabla compilada de respaldo. La llevaba, y los valores se habían
 * separado de los JSON hasta el punto de que los atributos base compilados de un saiyan
 * ({10,10,12,8,6,10}) eran casi el DOBLE de los del datapack ({7,5,6,4,3,10}). Un fallo de
 * carga no producía un error: producía un personaje silenciosamente inflado, imposible de
 * distinguir de uno legítimo salvo comparando números a mano.
 * Ahora un dato que falta es un fallo ruidoso: replaceAll y replaceBases validan que estén las
 * quince combinaciones y las cinco razas, y lanzan MissingRaceStatsException con la lista de lo
 * que falta. RaceStatManager deja que la excepción suba, así que el arranque del mundo o el
 * /reload fallan con un mensaje que dice exactamente qué archivo arreglar.
 * Los JSON del propio mod (data/zenkai/zenkai_race_stats/*.json) forman parte del jar, así que
 * el único camino a este error es que alguien los borre o los rompa a mano — que es justo
 * cuando quieres enterarte.
 */
public final class RaceStatTable {
    private RaceStatTable() {}

    /** Se lanza cuando el datapack no cubre cada una de las razas o estilos. */
    public static class MissingRaceStatsException extends IllegalStateException {
        public MissingRaceStatsException(String message) { super(message); }
    }

    /** Orden de las columnas de cada fila. Añadir SIEMPRE al final: el packet de sync
     *  serializa por posición y RaceStatManager rellena por índice. */
    public enum Col { MELEE, DEFENSE, HEALTH, STAMINA, KI_DMG, KI_RES, KI_COST, STAM_COST }

    /** Número de columnas por fila. Lo usan el manager y el packet; no lo hardcodees. */
    public static final int COLS = Col.values().length;

    /** Número de atributos base por raza (STR, CON, DEX, WIL, SPI, MND). */
    public static final int BASE_ATTRS = 6;

    private static volatile Map<Race, Map<Style, double[]>> TABLE = Map.of();
    private static volatile Map<Race, int[]> BASES = Map.of();

    /** ¿Hay datos cargados? Lo consultan las pantallas de cliente antes del primer sync. */
    public static boolean isLoaded() { return !TABLE.isEmpty() && !BASES.isEmpty(); }

    // ── Sustitución con validación ───────────────────────────────────────────

    /**
     * Sustituye la tabla entera (datapack en servidor, packet en cliente).
     * @throws MissingRaceStatsException si falta alguna de las 15 combinaciones.
     */
    public static void replaceAll(Map<Race, Map<Style, double[]>> loaded) {
        if (loaded == null) throw new MissingRaceStatsException(
                "zenkai_race_stats: no se cargó ninguna raza (carpeta vacía o datapack ausente)");

        List<String> missing = new ArrayList<>();
        for (Race r : Race.values()) {
            Map<Style, double[]> byStyle = loaded.get(r);
            if (byStyle == null) {
                missing.add(r.name().toLowerCase() + ".json (archivo entero)");
                continue;
            }
            for (Style s : Style.values()) {
                double[] row = byStyle.get(s);
                if (row == null) {
                    missing.add(r.name().toLowerCase() + ".json → \"" + s.name().toLowerCase() + "\"");
                } else if (row.length < COLS) {
                    missing.add(r.name().toLowerCase() + ".json → \"" + s.name().toLowerCase()
                            + "\" tiene " + row.length + " columnas, se esperaban " + COLS);
                }
            }
        }
        if (!missing.isEmpty()) throw new MissingRaceStatsException(
                "zenkai_race_stats incompleto. Falta:\n  - " + String.join("\n  - ", missing));

        TABLE = Map.copyOf(loaded);
    }

    /**
     * Sustituye los atributos base.
     * @throws MissingRaceStatsException si falta alguna raza o la fila está corta.
     */
    public static void replaceBases(Map<Race, int[]> loaded) {
        if (loaded == null) throw new MissingRaceStatsException(
                "zenkai_race_stats: no llegó ningún bloque \"base_attributes\"");

        List<String> missing = new ArrayList<>();
        for (Race r : Race.values()) {
            int[] row = loaded.get(r);
            if (row == null) {
                missing.add(r.name().toLowerCase() + ".json → \"base_attributes\"");
            } else if (row.length < BASE_ATTRS) {
                missing.add(r.name().toLowerCase() + ".json → \"base_attributes\" tiene "
                        + row.length + " valores, se esperaban " + BASE_ATTRS);
            }
        }
        if (!missing.isEmpty()) throw new MissingRaceStatsException(
                "zenkai_race_stats incompleto. Falta:\n  - " + String.join("\n  - ", missing));

        Map<Race, int[]> copy = new EnumMap<>(Race.class);
        for (var e : loaded.entrySet()) copy.put(e.getKey(), e.getValue().clone());
        BASES = Map.copyOf(copy);
    }

    // ── Consulta ─────────────────────────────────────────────────────────────

    /** Copia de los atributos de salida, indexada por ZenkaiAttributes.ordinal(). */
    public static int[] baseAttributes(Race race) {
        int[] row = BASES.get(race);
        if (row == null) {
            // No se lanza aquí: esto se llama desde el constructor del attachment, que corre
            // antes del primer sync en el cliente. Devolver ceros es visible al instante
            // (cada atributo a 0 en la pantalla) y se corrige solo cuando llega el
            // paquete; lanzar reventaría el login por una condición transitoria y normal.
            return new int[BASE_ATTRS];
        }
        return row.clone();
    }

    /** Fila cruda de una combinación, o null si aún no hay datos. Para serializar en el packet. */
    public static double[] row(Race race, Style style) {
        Map<Style, double[]> byStyle = TABLE.get(race);
        return (byStyle == null) ? null : byStyle.get(style);
    }

    /**
     * Coeficiente de una columna.
     * Devuelve 0.0 ante una combinación desconocida, NO 1.0. El 1.0 era un valor plausible que
     * se colaba en el juego sin llamar la atención — un jugador con coeficientes a 1.0 tiene
     * stats bajos pero coherentes y puede jugar horas sin notar nada raro. Con 0.0 los stats
     * salen a cero y el problema es imposible de pasar por alto en la primera pantalla.
     * En la práctica solo se ve en el cliente durante el instante entre el login y el sync.
     */
    public static double get(Race race, Style style, Col col) {
        if (race == null || style == null) return 0.0;
        double[] row = row(race, style);
        if (row == null || col.ordinal() >= row.length) return 0.0;
        return row[col.ordinal()];
    }

    public static double melee(Race r, Style s)      { return get(r, s, Col.MELEE); }
    public static double defense(Race r, Style s)    { return get(r, s, Col.DEFENSE); }
    public static double health(Race r, Style s)     { return get(r, s, Col.HEALTH); }
    public static double stamina(Race r, Style s)    { return get(r, s, Col.STAMINA); }
    public static double kiDamage(Race r, Style s)   { return get(r, s, Col.KI_DMG); }
    public static double kiReserves(Race r, Style s) { return get(r, s, Col.KI_RES); }

    /** Multiplicador de coste de ki. 1.0 = neutro. Cae a 1.0 y no a 0.0 sin datos: un coste
     *  multiplicado por cero sería ki GRATIS, que es un fallo mucho peor que un stat a cero. */
    public static double kiCostMult(Race r, Style s) {
        double v = get(r, s, Col.KI_COST);
        return v <= 0.0 ? 1.0 : v;
    }

    /** Multiplicador de coste de estamina. Mismo criterio que kiCostMult. */
    public static double staminaCostMult(Race r, Style s) {
        double v = get(r, s, Col.STAM_COST);
        return v <= 0.0 ? 1.0 : v;
    }

    /** Columna que corresponde a cada atributo. CONSTITUTION mapea a HEALTH: para la
     *  estamina hay que pedir STAMINA explícitamente (es el punto de separarlas). */
    public static Col colFor(ZenkaiAttributes attr) {
        return switch (attr) {
            case STRENGTH     -> Col.MELEE;
            case DEXTERITY    -> Col.DEFENSE;
            case CONSTITUTION -> Col.HEALTH;
            case WILLPOWER    -> Col.KI_DMG;
            case SPIRIT       -> Col.KI_RES;
            case MIND         -> null;   // MND no da stat de combate: es requisito
        };
    }
}