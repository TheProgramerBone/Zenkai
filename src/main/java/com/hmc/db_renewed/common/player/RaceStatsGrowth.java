package com.hmc.db_renewed.common.player;

import com.hmc.db_renewed.common.race.Race;

import java.util.Map;

public record RaceStatsGrowth(
        float strengthMultiplier,
        float dexterityMultiplier,
        float constitutionMultiplier,
        float willpowerMultiplier,
        float mindMultiplier,
        float spiritMultiplier
) {
    public static final Map<Race, RaceStatsGrowth> GROWTHS = Map.of(
            Race.HUMAN,     new RaceStatsGrowth(1.0f, 1.0f, 1.0f, 1.0f, 1.5f, 1.0f),
            Race.SAIYAN,    new RaceStatsGrowth(1.6f, 1.2f, 1.1f, 1.0f, 0.7f, 0.9f),
            Race.NAMEKIAN,  new RaceStatsGrowth(1.1f, 1.0f, 1.3f, 1.3f, 1.0f, 1.2f),
            Race.COLD_DEMON,   new RaceStatsGrowth(1.3f, 1.5f, 1.0f, 1.1f, 0.9f, 1.1f),
            Race.MAJIN,     new RaceStatsGrowth(1.0f, 1.0f, 1.4f, 1.0f, 1.0f, 1.1f)
    );
}
