package com.hmc.zenkai.feature;

import java.util.EnumMap;
import java.util.Map;

/**
 * Cuánto rinde UN punto de atributo, por raza y estilo. Sustituye al producto
 * raceMultipliers[i] × styleMultipliers[i], que tenía dos límites:
 *   - la matriz de 15 combinaciones quedaba determinada por 5+3 vectores, así que no se
 *     podían afinar casillas sueltas;
 *   - HEALTH y STAMINA compartían el multiplicador de CON, de modo que su ratio era el
 *     mismo para todas las razas. Aquí son columnas independientes: Majin es un tanque
 *     (52 HP/punto) sin ser un pozo de estamina, y Namekian al revés.
 *
 * Columnas: MELEE=STR · DEFENSE=DEX · HEALTH y STAMINA=CON · KI_DMG=WIL · KI_RES=SPI.
 * DEX ya NO alimenta la velocidad (eso vive en las habilidades run/fly), así que subirla
 * es puramente defensivo.
 *
 * Los valores VIVEN EN DATAPACK (data/&lt;ns&gt;/zenkai_race_stats/&lt;raza&gt;.json, ver
 * RaceStatManager) y se sincronizan al cliente con RaceStatSyncPacket. Esta clase es la
 * fachada de consulta: el resto del mod solo la llama a ella, así que el cambio de origen
 * de los datos no toca a nadie más.
 *
 * La tabla de abajo son los DEFAULTS: se usan si el datapack no define una raza (o si falla
 * la carga), de modo que un JSON con una errata no deja a nadie con stats a cero.
 */
public final class RaceStatTable {
    private RaceStatTable() {}

    /** Orden de las columnas de cada fila. */
    public enum Col { MELEE, DEFENSE, HEALTH, STAMINA, KI_DMG, KI_RES }

    /**
     * Atributos de salida por raza, INDEXADOS POR ZenkaiAttributes.ordinal()
     * (STR, CON, DEX, WIL, SPI, MND). Antes vivían en config como listas posicionales
     * documentadas en OTRO orden ([STR, DEX, CON, ...]) que no coincidía con el consumidor:
     * CON y DEX salían intercambiadas. En el datapack van con claves nombradas.
     */
    private static final Map<Race, int[]> DEFAULT_BASES = new EnumMap<>(Race.class);
    private static volatile Map<Race, int[]> BASES = Map.of();

    static {
        //                        STR  CON  DEX  WIL  SPI  MND
        DEFAULT_BASES.put(Race.HUMAN,    new int[]{10, 10, 10, 10, 10, 10});
        DEFAULT_BASES.put(Race.SAIYAN,   new int[]{14, 10, 12,  8,  6, 10});
        DEFAULT_BASES.put(Race.NAMEKIAN, new int[]{ 8,  8, 10, 11, 13, 10});
        DEFAULT_BASES.put(Race.ARCOSIAN, new int[]{ 8,  8, 10, 12, 12, 10});
        DEFAULT_BASES.put(Race.MAJIN,    new int[]{10,  8, 10,  8, 10, 10});
    }

    /** Copia de los atributos de salida, indexada por ZenkaiAttributes.ordinal(). */
    public static int[] baseAttributes(Race race) {
        int[] row = BASES.get(race);
        if (row == null) row = DEFAULT_BASES.get(race);
        return (row == null) ? new int[]{10, 10, 10, 10, 10, 10} : row.clone();
    }

    /** Sustituye las bases (datapack en servidor, packet en cliente). */
    public static void replaceBases(Map<Race, int[]> loaded) {
        BASES = (loaded == null) ? Map.of() : Map.copyOf(loaded);
    }

    /** Defaults compilados: el suelo si el datapack no cubre una combinación. */
    private static final Map<Race, Map<Style, double[]>> DEFAULTS = new EnumMap<>(Race.class);

    /** Lo que manda: lo rellena RaceStatManager en cada /reload y el packet en el cliente. */
    private static volatile Map<Race, Map<Style, double[]>> TABLE = Map.of();

    private static void put(Race r, Style s, double melee, double defense, double health,
                            double stamina, double kiDmg, double kiRes) {
        DEFAULTS.computeIfAbsent(r, k -> new EnumMap<>(Style.class))
                .put(s, new double[]{melee, defense, health, stamina, kiDmg, kiRes});
    }

    /** Sustituye la tabla entera (datapack en servidor, packet en cliente). */
    public static void replaceAll(Map<Race, Map<Style, double[]>> loaded) {
        TABLE = (loaded == null) ? Map.of() : Map.copyOf(loaded);
    }

    /** Fila cruda de una combinación, o null. Para serializar en el packet. */
    public static double[] row(Race race, Style style) {
        Map<Style, double[]> byStyle = TABLE.get(race);
        double[] row = (byStyle == null) ? null : byStyle.get(style);
        if (row != null) return row;
        byStyle = DEFAULTS.get(race);
        return (byStyle == null) ? null : byStyle.get(style);
    }

    static {
        //                              melee  def  health stam  kiDmg kiRes
        put(Race.SAIYAN,   Style.WARRIOR,        11.0, 4.6,  28, 13.0,  4.2,  40);
        put(Race.SAIYAN,   Style.MARTIAL_ARTIST,  8.2, 5.6,  34, 15.5,  5.6,  62);
        put(Race.SAIYAN,   Style.SPIRITUALIST,    4.6, 4.2,  25,  7.0,  8.4,  78);

        put(Race.ARCOSIAN, Style.WARRIOR,         9.4, 7.4,  21, 12.5,  4.6,  44);
        put(Race.ARCOSIAN, Style.MARTIAL_ARTIST,  6.9, 8.8,  25, 15.0,  6.2,  68);
        put(Race.ARCOSIAN, Style.SPIRITUALIST,    3.8, 6.8,  19,  6.8,  9.2,  84);

        put(Race.HUMAN,    Style.WARRIOR,         9.8, 5.4,  32, 15.0,  4.0,  46);
        put(Race.HUMAN,    Style.MARTIAL_ARTIST,  7.2, 6.4,  38, 18.0,  5.2,  70);
        put(Race.HUMAN,    Style.SPIRITUALIST,    4.2, 5.0,  28,  8.0,  8.0,  86);

        put(Race.NAMEKIAN, Style.WARRIOR,         8.6, 4.4,  34, 15.5,  4.4,  62);
        put(Race.NAMEKIAN, Style.MARTIAL_ARTIST,  6.3, 5.2,  40, 18.5,  5.8,  95);
        put(Race.NAMEKIAN, Style.SPIRITUALIST,    3.4, 3.9,  30,  8.5, 10.0, 120);

        put(Race.MAJIN,    Style.WARRIOR,         9.6, 3.8,  52, 14.5,  4.1,  44);
        put(Race.MAJIN,    Style.MARTIAL_ARTIST,  7.0, 4.4,  64, 17.0,  5.4,  66);
        put(Race.MAJIN,    Style.SPIRITUALIST,    3.9, 3.4,  47,  7.6,  8.2,  82);
    }

    /** Coeficiente de una columna. Devuelve 1.0 ante combinaciones desconocidas: los
     *  atributos siguen contando, así un dato corrupto no deja al jugador a cero. */
    public static double get(Race race, Style style, Col col) {
        if (race == null || style == null) return 1.0;
        double[] row = row(race, style);
        return row == null ? 1.0 : row[col.ordinal()];
    }

    public static double melee(Race r, Style s)      { return get(r, s, Col.MELEE); }
    public static double defense(Race r, Style s)    { return get(r, s, Col.DEFENSE); }
    public static double health(Race r, Style s)     { return get(r, s, Col.HEALTH); }
    public static double stamina(Race r, Style s)    { return get(r, s, Col.STAMINA); }
    public static double kiDamage(Race r, Style s)   { return get(r, s, Col.KI_DMG); }
    public static double kiReserves(Race r, Style s) { return get(r, s, Col.KI_RES); }

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