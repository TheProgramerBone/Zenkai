package com.hmc.db_renewed.data;

import java.util.EnumMap;
import java.util.Map;

public class StatSet {
    private final EnumMap<StatType, Float> stats = new EnumMap<>(StatType.class);

    public StatSet(float defaultValue) {
        for (StatType type : StatType.values()) {
            stats.put(type, defaultValue);
        }
    }

    public float get(StatType type) {
        return stats.getOrDefault(type, 0f);
    }

    public void set(StatType type, float value) {
        stats.put(type, value);
    }

    public void add(StatType type, float value) {
        stats.put(type, get(type) + value);
    }

    public Map<StatType, Float> asMap() {
        return stats;
    }
}