package com.hmc.zenkai.event.tick;

import com.hmc.zenkai.feature.player.PlayerFormAttachment;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.feature.player.PlayerVisualAttachment;
import net.minecraft.world.entity.player.Player;

/**
 * Contexto de un tick de jugador: los cuatro objetos que casi todos los sistemas necesitan,
 * resueltos UNA vez por el orquestador (antes cada sección los volvía a pedir).
 * turboOn NO va aquí a propósito: se calcula después de los gates y solo lo usan
 * FlightSystem y GroundMovementSystem, así que viaja como parámetro explícito.
 */
public record TickCtx(Player p,
                      PlayerStatsAttachment att,
                      PlayerFormAttachment form,
                      PlayerVisualAttachment visual) {
}