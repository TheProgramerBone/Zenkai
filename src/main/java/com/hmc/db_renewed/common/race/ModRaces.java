package com.hmc.db_renewed.common.race;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ModRaces {

    private static final Map<String, Map<String, Double>> RACE_STATS = new HashMap<>();

    static {
        // STR, DEX, CON, WIL, SPI, MND
        register("human", new double[]{10, 10, 10, 10, 10, 10});
        register("saiyan", new double[]{14, 10, 12, 8, 6, 6});
        register("namek", new double[]{8, 8, 12, 12, 12, 8});
        register("cold_demon", new double[]{12, 12, 12, 6, 8, 10});
        register("majin", new double[]{10, 8, 14, 6, 10, 12});
    }

    private static void register(String id, double[] stats) {
        Map<String, Double> map = new HashMap<>();
        map.put("STR", stats[0]);
        map.put("DEX", stats[1]);
        map.put("CON", stats[2]);
        map.put("WIL", stats[3]);
        map.put("SPI", stats[4]);
        map.put("MND", stats[5]);
        RACE_STATS.put(id, map);
    }

    public static double getBaseStat(String raceId, String stat) {
        Map<String, Double> stats = RACE_STATS.getOrDefault(raceId, RACE_STATS.get("human"));
        return stats.getOrDefault(stat, 0.0);
    }

    public static double getModifier(String raceId, String stat) {
        return switch (raceId.toLowerCase()) {
            case "saiyan" -> switch (stat.toLowerCase()) {
                case "str" -> 1.5;
                case "dex" -> 1.2;
                case "con" -> 1.0;
                case "wil" -> 0.8;
                case "spi" -> 0.6;
                case "mnd" -> 0.6;
                default -> 1.0;
            };
            case "human" -> switch (stat.toLowerCase()) {
                case "str", "dex", "con", "wil", "spi", "mnd" -> 1.0;
                default -> 1.0;
            };
            case "namek" -> switch (stat.toLowerCase()) {
                case "wil" -> 1.4;
                case "spi" -> 1.4;
                case "str" -> 0.8;
                case "dex" -> 0.9;
                case "con" -> 1.1;
                case "mnd" -> 1.2;
                default -> 1.0;
            };
            case "cold_demon" -> switch (stat.toLowerCase()) {
                case "dex" -> 1.5;
                case "str" -> 1.2;
                case "con" -> 0.9;
                case "wil" -> 0.8;
                case "spi" -> 1.0;
                case "mnd" -> 0.8;
                default -> 1.0;
            };
            case "majin" -> switch (stat.toLowerCase()) {
                case "con" -> 1.6;
                case "wil" -> 1.1;
                case "str" -> 0.9;
                case "dex" -> 0.7;
                case "spi" -> 1.0;
                case "mnd" -> 1.0;
                default -> 1.0;
            };
            default -> 1.0;
        };
    }


    public static List<String> getAllRaceIds() {
        return new ArrayList<>(RACE_STATS.keySet());
    }
}