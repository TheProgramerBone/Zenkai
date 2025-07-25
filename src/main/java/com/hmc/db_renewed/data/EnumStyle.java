package com.hmc.db_renewed.data;

public enum EnumStyle {
    WARRIOR,
    MARTIAL_ARTIST,
    SPIRITUALIST;

    public String getSerializedName() {
        return this.name().toLowerCase(); // "STRENGTH" -> "strength"
    }
}