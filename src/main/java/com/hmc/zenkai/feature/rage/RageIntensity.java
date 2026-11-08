package com.hmc.zenkai.feature.rage;

import com.hmc.zenkai.config.ServerConfig;

/** Intensidad de un acontecimiento. Solo mide cuánta maestría vale; NUNCA concede una forma por sí sola. */
public enum RageIntensity {
    LOW, MEDIUM, HIGH, EXCEPTIONAL;

    /** Minutos equivalentes de maestría (ServerConfig {@code rage.minutes_*}). */
    public double minutes() {
        return switch (this) {
            case LOW -> ServerConfig.RAGE_MINUTES_LOW.get();
            case MEDIUM -> ServerConfig.RAGE_MINUTES_MEDIUM.get();
            case HIGH -> ServerConfig.RAGE_MINUTES_HIGH.get();
            case EXCEPTIONAL -> ServerConfig.RAGE_MINUTES_EXCEPTIONAL.get();
        };
    }

    public boolean atLeast(RageIntensity min) { return ordinal() >= min.ordinal(); }

    public static RageIntensity parse(String s, RageIntensity fallback) {
        if (s == null) return fallback;
        try { return valueOf(s.trim().toUpperCase(java.util.Locale.ROOT)); } catch (IllegalArgumentException e) { return fallback; }
    }
}
