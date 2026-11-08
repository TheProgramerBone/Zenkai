package com.hmc.zenkai.feature.rage;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;

/**
 * Un jugador sobrevivió al borde y se recuperó: bajó del 20 % de su body máximo (o cayó derribado) y volvió al 60 % o más.
 * Aplica a las cinco razas. Enganche de las rage forms (CRITICAL_SURVIVAL, intensidad Excepcional si hay un aliado caído
 * cerca; esa condición la comprueba quien escucha). Se publica UNA vez por ciclo, sin enfriamiento propio: el del sistema de
 * recompensas es el que frena. No se puede cancelar.
 *
 * @param wasDowned en algún momento del ciclo estuvo derribado (lo levantaron o se curó), no solo cerca del borde
 */
public class CriticalSurvivalEvent extends Event {
    private final ServerPlayer survivor;
    private final boolean wasDowned;

    public CriticalSurvivalEvent(ServerPlayer survivor, boolean wasDowned) {
        this.survivor = survivor;
        this.wasDowned = wasDowned;
    }

    public ServerPlayer survivor() { return survivor; }
    public boolean wasDowned() { return wasDowned; }
}
