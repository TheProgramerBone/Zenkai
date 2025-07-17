package com.hmc.db_renewed.common.player;

import com.hmc.db_renewed.common.race.ModRaces;

import java.util.Map;

public record RaceStatsGrowth(
        float strengthMultiplier,
        float dexterityMultiplier,
        float constitutionMultiplier,
        float willpowerMultiplier,
        float mindMultiplier,
        float spiritMultiplier
) {
    public static final Map<ModRaces, RaceStatsGrowth> GROWTHS = Map.of(
            ModRaces.HUMAN,     new RaceStatsGrowth(1.0f, 1.0f, 1.0f, 1.0f, 1.5f, 1.0f),
            ModRaces.SAIYAN,    new RaceStatsGrowth(1.6f, 1.2f, 1.1f, 1.0f, 0.7f, 0.9f),
            ModRaces.NAMEKIAN,  new RaceStatsGrowth(1.1f, 1.0f, 1.3f, 1.3f, 1.0f, 1.2f),
            ModRaces.COLD_DEMON,   new RaceStatsGrowth(1.3f, 1.5f, 1.0f, 1.1f, 0.9f, 1.1f),
            ModRaces.MAJIN,     new RaceStatsGrowth(1.0f, 1.0f, 1.4f, 1.0f, 1.0f, 1.1f)
    );
}
