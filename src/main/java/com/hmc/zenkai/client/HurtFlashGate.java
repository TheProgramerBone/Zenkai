package com.hmc.zenkai.client;

import net.minecraft.client.Minecraft;

/**
 * Discrimina, en cliente, entre una bajada de vida por DAÑO REAL y una por sincronización.
 * Existe porque el espejo body→vanilla hace que cualquier drenaje (kaioken, y lo que venga
 * después) llegue como ClientboundSetHealthPacket, y LocalPlayer.hurtTo pone hurtTime a 10
 * en toda bajada. Con el kaioken drenando varios puntos POR TICK eso es un flash rojo y un
 * tilt de cámara continuos: no es información, es ruido, y marea.
 * La animación de hurtTo es redundante para el daño real: ese ya trae su propio
 * ClientboundDamageEventPacket, que además pone el sonido y la dirección del golpe. Así que
 * marcamos cuándo llegó uno de esos y silenciamos el resto.
 */
public final class HurtFlashGate {
    private HurtFlashGate() {}

    /** Ventana en ticks. No es 0 porque los dos paquetes salen del mismo tick de servidor
     *  pero no hay garantía de que el cliente los procese en el mismo tick suyo. */
    private static final long WINDOW = 2L;

    /** Lo llama el mixin de handleDamageEvent cuando el golpeado es el jugador local. */
    public static void markRealDamage() {
        Minecraft mc = Minecraft.getInstance();
        lastRealDamageTick = mc.level == null ? Long.MIN_VALUE : mc.level.getGameTime();
    }

    /** Centinela propio en vez de Long.MIN_VALUE: restar MIN_VALUE desborda el long y la
     *  comparación sale siempre true, o sea el filtro nunca filtra. */
    private static final long NEVER = -1L;

    private static long lastRealDamageTick = NEVER;

    public static boolean recentRealDamage() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return true;
        if (lastRealDamageTick == NEVER) return false;
        return mc.level.getGameTime() - lastRealDamageTick <= WINDOW;
    }
}