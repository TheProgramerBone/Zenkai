package com.hmc.zenkai.core.network.feature;

public enum Dbrattributes {
    STRENGTH, CONSTITUTION, DEXTERITY, WILLPOWER, SPIRIT, MIND;
    public static Dbrattributes fromString(String s) {
        return Dbrattributes.valueOf(s.toUpperCase());
    }
}