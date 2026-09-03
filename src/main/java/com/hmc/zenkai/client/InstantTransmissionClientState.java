package com.hmc.zenkai.client;

/**
 * Espejo cliente del cooldown de Transmisión Instantánea, solo para el badge de HUD
 * (ClientZenkaiHooks). Se actualiza vía InstantTransmissionSyncPacket (S2C, self-only) — no es
 * un attachment ni necesita persistencia, es puro estado de HUD.
 */
public final class InstantTransmissionClientState {
    private InstantTransmissionClientState() {}

    private static int cooldownTicks = 0;

    public static void setCooldownTicks(int ticks) { cooldownTicks = Math.max(0, ticks); }

    public static int cooldownTicks() { return cooldownTicks; }

    public static boolean onCooldown() { return cooldownTicks > 0; }
}
