package com.hmc.zenkai.client.combat;

import net.minecraft.client.Minecraft;

/**
 * Aguante de guardia del jugador local (lo llena GuardSyncPacket). La barra del HUD se pinta
 * solo mientras bloquea o mientras se regenera, es decir, cuando no está lleno.
 */
public final class GuardClientState {
    private GuardClientState() {}

    private static volatile float current = -1f;
    private static volatile float max = 0f;
    /** Tick de cliente del último dato recibido; la barra se queda un instante al llenarse. */
    private static volatile long lastUpdate = -100000L;

    /** Tick de cliente hasta el que la guardia está rota (no puede bloquear). */
    private static volatile long brokenUntil = 0L;

    public static void accept(float cur, float mx, int brokenTicks) {
        current = cur;
        max = mx;
        var level = Minecraft.getInstance().level;
        long now = level == null ? 0L : level.getGameTime();
        lastUpdate = now;
        if (brokenTicks >= 0) brokenUntil = now + brokenTicks;
    }

    public static boolean broken() {
        var level = Minecraft.getInstance().level;
        return level != null && brokenUntil > level.getGameTime();
    }

    /** 0..1, o -1 si no hay dato o la función está apagada. */
    public static float fraction() {
        if (max <= 0f || current < 0f) return -1f;
        return Math.max(0f, Math.min(1f, current / max));
    }

    /** ¿Debe verse la barra? No lleno, o recién llenado (medio segundo). */
    public static boolean visible() {
        float f = fraction();
        if (f < 0f) return false;
        if (f < 1f) return true;
        var level = Minecraft.getInstance().level;
        return level != null && level.getGameTime() - lastUpdate < 10;
    }

    public static void reset() { current = -1f; max = 0f; brokenUntil = 0L; }
}
