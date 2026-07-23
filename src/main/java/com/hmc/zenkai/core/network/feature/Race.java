package com.hmc.zenkai.core.network.feature;

public enum Race {
    HUMAN(1.0), SAIYAN(1.0), NAMEKIAN(1.0), ARCOSIAN(0.8), MAJIN(1.0);

    private final double baseScale;

    Race(double baseScale) { this.baseScale = baseScale; }

    /** Escala del jugador SIN transformación (minecraft:generic.scale).
     *  Las formas la SUSTITUYEN con su propio "scale" del datapack, no la multiplican. */
    public double baseScale() { return baseScale; }
}