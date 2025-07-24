package com.hmc.db_renewed.common.stats;

import com.hmc.db_renewed.api.PlayerStatData;
import com.hmc.db_renewed.common.race.ModRaces;
import com.hmc.db_renewed.common.style.ModCombatStyles;

import java.util.HashMap;
import java.util.Map;

public class StatCalculator {

    public static Map<String, Integer> calculateFinalStats(PlayerStatData data) {
        Map<String, Integer> result = new HashMap<>();

        String raceId = data.getRaceId();
        String styleId = data.getCombatStyleId();
        Map<String, Integer> investedStats = data.getAllStats();

        for (String stat : investedStats.keySet()) {
            int tp = investedStats.get(stat);

            double base = ModRaces.getBaseStat(raceId, stat);
            double raceMultiplier = ModRaces.getModifier(raceId, stat);
            double styleMultiplier = ModCombatStyles.getModifier(styleId, stat);
            double total = base + tp * (raceMultiplier + styleMultiplier);
            result.put(stat, (int) Math.round(total));
        }
        return result;
    }
}