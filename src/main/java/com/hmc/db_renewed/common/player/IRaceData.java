package com.hmc.db_renewed.common.player;

import com.hmc.db_renewed.common.capability.StatAllocation;
import com.hmc.db_renewed.common.race.ModRaces;

public interface IRaceData {
    ModRaces getRace();
    void setRace(ModRaces modRaces);

    boolean hasSelectedRace();
    void setSelectedRace(boolean selected);

    StatAllocation getStats();
    void setStats(StatAllocation stats);
}