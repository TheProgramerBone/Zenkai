package com.hmc.db_renewed.common.race;

import com.hmc.db_renewed.common.capability.StatAllocation;

import java.util.Map;

public class ModRacesStats {
    public static final Map<ModRaces, StatAllocation> DEFAULT_STATS = Map.of(
            ModRaces.HUMAN, new StatAllocation(10, 10, 10, 10, 10, 10),
            ModRaces.SAIYAN, new StatAllocation(15, 12, 8, 14, 6, 9),
            ModRaces.NAMEKIAN, new StatAllocation(9, 9, 14, 10, 10, 12),
            ModRaces.COLD_DEMON, new StatAllocation(13, 14, 10, 13, 8, 10),
            ModRaces.MAJIN, new StatAllocation(12, 8, 15, 9, 7, 13)
    );
}
