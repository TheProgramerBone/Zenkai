package com.hmc.db_renewed.client;

import com.hmc.db_renewed.common.stats.PlayerStats;

public class ClientPlayerStats {

    private static PlayerStats currentStats = new PlayerStats(0, 0, 0, 0, 0, 0);

    public static void set(PlayerStats stats) {
        currentStats = stats;
    }

    public static PlayerStats get() {
        return currentStats;
    }
}