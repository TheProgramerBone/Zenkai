package com.hmc.db_renewed.common.player;

import com.hmc.db_renewed.common.race.Race;
import com.hmc.db_renewed.common.race.RaceStats;

public interface IRaceData {
    Race getRace();
    void setRace(Race race);

    boolean hasSelectedRace();
    void setSelectedRace(boolean selected);

    RaceStats getStats();
    void setStats(RaceStats stats);
}