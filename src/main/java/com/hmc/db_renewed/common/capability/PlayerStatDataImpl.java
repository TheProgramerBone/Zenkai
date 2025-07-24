package com.hmc.db_renewed.common.capability;

import com.hmc.db_renewed.api.PlayerStatData;

import java.util.HashMap;
import java.util.Map;

public class PlayerStatDataImpl implements PlayerStatData {
    private String raceId = "human";
    private String styleId = "warrior";
    private int bodyType = -1;
    private int hairType = -1;
    private String skinColor = "#f4c7b4";

    private final Map<String, Integer> stats = new HashMap<>();
    private final Map<String, Integer> costMap = new HashMap<>();
    private int totalTP = 0;

    public PlayerStatDataImpl() {
        for (String stat : new String[]{"STR", "DEX", "CON", "WIL", "SPI", "MND"}) {
            stats.put(stat, 0);        // TP invertido en cada stat
            costMap.put(stat, 1);      // Costo inicial para subir stat
        }
    }


    //CUSTOMIZACIÓN
    @Override
    public String getSkinColor() { return skinColor; }
    @Override
    public void setSkinColor(String skinColor) { this.skinColor = skinColor; }

    @Override
    public int getbodyType() { return bodyType; }
    @Override
    public void setbodyType(int bodyType) { this.bodyType = bodyType; }

    @Override
    public int gethairType() { return hairType; }
    @Override
    public void sethairType(int hairType) { this.hairType = hairType; }

    // RAZA Y ESTILO
    @Override
    public boolean isCharacterCreated() {
        return raceId != null && styleId != null
                && !skinColor.isEmpty()
                && bodyType != -1 && hairType != -1;
    }
    @Override public String getRaceId() { return raceId; }
    @Override public void setRaceId(String raceId) { this.raceId = raceId; }

    @Override public String getCombatStyleId() { return styleId; }
    @Override public void setCombatStyleId(String styleId) { this.styleId = styleId; }

    // STATS
    @Override public int getStat(String stat) { return stats.getOrDefault(stat, 0); }
    @Override public void setStat(String stat, int value) { stats.put(stat, value); }

    @Override public Map<String, Integer> getAllStats() { return stats; }

    // TP
    @Override public int getTotalTP() { return totalTP; }
    @Override public void setTotalTP(int tp) { this.totalTP = tp; }

    @Override public int getCostToIncrease(String stat) { return costMap.getOrDefault(stat, 1); }
    @Override public void increaseCost(String stat) {
        costMap.put(stat, getCostToIncrease(stat) + 1); // puede cambiarse a curva logarítmica
    }

    @Override public Map<String, Integer> getCostMap() { return costMap; }

    @Override
    public void resetCharacterCreation() {
        this.raceId = null;
        this.styleId = null;
        this.bodyType = -1;
        this.hairType = -1;
        this.skinColor = "";
        this.stats.replaceAll((k, v) -> 0);
        this.costMap.replaceAll((k, v) -> 1);
        this.totalTP = 0;
    }

    public boolean investTPIntoStat(String stat) {
        int currentTP = getTotalTP();
        int cost = getCostToIncrease(stat);

        if (currentTP < cost) {
            return false; // No tienes suficiente TP
        }

        // Gasta el TP
        setTotalTP(currentTP - cost);

        // Sube el stat
        setStat(stat, getStat(stat) + 1);

        // Aumenta el costo para la próxima
        increaseCost(stat);

        return true;
    }
}
