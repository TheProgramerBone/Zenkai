package com.hmc.zenkai.core.network.feature;

public enum ZenkaiAttributes {
    STRENGTH, CONSTITUTION, DEXTERITY, WILLPOWER, SPIRIT, MIND;
    public static ZenkaiAttributes fromString(String s) {
        return ZenkaiAttributes.valueOf(s.toUpperCase());
    }
}