package com.hmc.db_renewed.common.style;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ModCombatStyles {

    private static final Map<String, Map<String, Double>> STYLE_MODIFIERS = new HashMap<>();

    static {
        register("warrior", new double[]{1.2, 1.1, 1.3, 0.9, 0.8, 0.7});
        register("martial_artist", new double[]{1.1, 1.3, 1.0, 1.0, 1.0, 0.9});
        register("spiritualist", new double[]{0.8, 1.0, 0.9, 1.1, 1.3, 1.2});
    }

    private static void register(String id, double[] mods) {
        Map<String, Double> map = new HashMap<>();
        map.put("STR", mods[0]);
        map.put("DEX", mods[1]);
        map.put("CON", mods[2]);
        map.put("WIL", mods[3]);
        map.put("SPI", mods[4]);
        map.put("MND", mods[5]);
        STYLE_MODIFIERS.put(id, map);
    }

    public static double getModifier(String styleId, String stat) {
        Map<String, Double> mods = STYLE_MODIFIERS.getOrDefault(styleId, STYLE_MODIFIERS.get("warrior"));
        return mods.getOrDefault(stat, 1.0);
    }

    public static List<String> getAllStyleIds() {
        return new ArrayList<>(STYLE_MODIFIERS.keySet());
    }
}