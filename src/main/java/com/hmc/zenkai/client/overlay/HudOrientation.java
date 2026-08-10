package com.hmc.zenkai.client.overlay;

/** Sentido en que se apilan las nueve celdas del HUD de técnicas. */
public enum HudOrientation {
    VERTICAL,
    HORIZONTAL;

    public boolean isHorizontal() { return this == HORIZONTAL; }

    public String nameKey() { return "config.zenkai.orientation." + name().toLowerCase(); }
}