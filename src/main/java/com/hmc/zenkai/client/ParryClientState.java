package com.hmc.zenkai.client;

import net.minecraft.client.Minecraft;

/**
 * Estado CLIENTE del parry: solo el cooldown que el servidor comunica al FALLAR (o al intentarlo
 * en cooldown), para pintar la insignia con su cuenta atrás en ClientZenkaiHooks. No decide nada.
 */
public final class ParryClientState {
    private ParryClientState() {}

    /** Tick de juego (cliente) en que vuelve a poder abrirse la ventana. 0 = nada que mostrar. */
    private static long readyAt = 0L;

    public static void onCooldown(int ticksRemaining) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        readyAt = ticksRemaining <= 0 ? 0L : mc.level.getGameTime() + ticksRemaining;
    }

    /** Ticks que faltan, o 0 si no hay aviso que mostrar. */
    public static int cooldownTicks() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || readyAt == 0L) return 0;
        long left = readyAt - mc.level.getGameTime();
        if (left <= 0) { readyAt = 0L; return 0; }
        return (int) left;
    }

    public static void clear() { readyAt = 0L; }
}
