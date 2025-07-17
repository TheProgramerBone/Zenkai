package com.hmc.db_renewed.common.style;

public enum CombatStyle {
    WARRIOR, MARTIAL_ARTIST, SPIRITUALIST;

    public static CombatStyle byName(String name) {
        for (CombatStyle race : CombatStyle.values()) {
            if (race.getSerializedName().equalsIgnoreCase(name)) {
                return race;
            }
        }
        return CombatStyle.WARRIOR; // fallback
    }

    public String getSerializedName() {
        return this.name().toLowerCase();
    }
}