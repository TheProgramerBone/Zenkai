package com.hmc.db_renewed.data;

public enum StatType {
    STRENGTH,
    CONSTITUTION,
    DEXTERITY,
    WILLPOWER,
    SPIRIT,
    MIND;

    public String getSerializedName() {
        return this.name().toLowerCase();
    }
}