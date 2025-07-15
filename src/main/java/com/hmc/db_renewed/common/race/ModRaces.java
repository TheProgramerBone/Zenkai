package com.hmc.db_renewed.common.race;

import java.util.Map;

public class ModRaces {
    public static final Map<Race, RaceStats> DEFAULT_STATS = Map.of(
            Race.HUMAN, new RaceStats(10, 10, 10, 10, 10, 10),
            Race.SAIYAN, new RaceStats(15, 12, 8, 14, 6, 9),
            Race.NAMEKIAN, new RaceStats(9, 9, 14, 10, 10, 12),
            Race.FREEZER, new RaceStats(13, 14, 10, 13, 8, 10),
            Race.MAJIN, new RaceStats(12, 8, 15, 9, 7, 13)
    );
}
