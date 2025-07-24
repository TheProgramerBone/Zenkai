package com.hmc.db_renewed.api;

import java.util.Map;

public interface PlayerStatData {
    //CUSTOMIZACIÓN

    void resetCharacterCreation();

    boolean investTPIntoStat(String stat);

    String getSkinColor();

    void setSkinColor(String raceId);

    int getbodyType();

    void setbodyType(int bodyType);

    int gethairType();

    void sethairType(int hairType);

    // RAZA Y ESTILO
    boolean isCharacterCreated();

    String getRaceId();
    void setRaceId(String raceId);

    String getCombatStyleId();
    void setCombatStyleId(String styleId);

    int getStat(String stat);
    void setStat(String stat, int value);
    Map<String, Integer> getAllStats();

    int getTotalTP();
    void setTotalTP(int tp);

    int getCostToIncrease(String stat);
    void increaseCost(String stat);
    Map<String, Integer> getCostMap();
}