package com.hmc.db_renewed.common.race;

public enum ModRaces {
    HUMAN,
    SAIYAN,
    NAMEKIAN,
    COLD_DEMON,
    MAJIN;

    public static ModRaces byName(String name) {
        for (ModRaces race : ModRaces.values()) {
            if (race.getSerializedName().equalsIgnoreCase(name)) {
                return race;
            }
        }
        return ModRaces.HUMAN; // fallback
    }

    public String getSerializedName() {
        return this.name().toLowerCase(); // o como prefieras serializarlo
    }
}