package com.hmc.zenkai.feature.teleport;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

/**
 * Registra el waypoint {@link GenericDimensionDestinations#END_OUTER_ISLAND_WAYPOINT} — llamado
 * DESDE {@code EndGatewayBlockMixin.zenkai$trackGatewayDestination} (la única llamante), que
 * intercepta {@code EndGatewayBlock.getPortalDestination} para leer el destino que vainilla YA
 * calculó, sin recalcular ningún enlace de Gateway por nuestra cuenta.
 *
 * FILTRO por distancia: el MISMO End Gateway sirve para los dos sentidos del viaje — de la isla
 * principal hacia una isla exterior, Y el "gateway de retorno" de esa isla exterior de vuelta al
 * spawn. Sin filtrar, un jugador que explora una isla y vuelve por el gateway de retorno
 * sobrescribiría el waypoint con las coordenadas del SPAWN — perdiendo justo el punto que se
 * supone que debía recordar. {@link #MIN_DISTANCE_FROM_MAIN_ISLAND} descarta cualquier destino
 * demasiado cerca de {@link GenericDimensionDestinations#END_MAIN_ISLAND} como para ser una isla
 * exterior de verdad (que en cualquier mundo vainilla empiezan mucho más lejos que esto) — un
 * viaje de vuelta simplemente no toca el waypoint, dejando el último destino real intacto.
 */
public final class EndOuterIslandTracker {
    private EndOuterIslandTracker() {}

    /** Bloques, distancia XZ desde el centro de la isla principal. */
    private static final double MIN_DISTANCE_FROM_MAIN_ISLAND = 200.0;

    public static void onGatewayTeleport(ServerPlayer sp, BlockPos dest) {
        BlockPos main = GenericDimensionDestinations.END_MAIN_ISLAND;
        double dx = dest.getX() - main.getX();
        double dz = dest.getZ() - main.getZ();
        if (dx * dx + dz * dz < MIN_DISTANCE_FROM_MAIN_ISLAND * MIN_DISTANCE_FROM_MAIN_ISLAND) {
            return; // viaje de VUELTA al spawn/isla principal — no cuenta como isla exterior
        }

        InstantTransmissionAttachment att = InstantTransmissionAttachment.get(sp);
        if (att.setWaypoint(GenericDimensionDestinations.END_OUTER_ISLAND_WAYPOINT, dest)) {
            // Solo resincroniza si de verdad cambió — mismo criterio que TeleportDiscoverySystem
            // con markDiscovered/markDimensionVisited, nunca en bucle.
            InstantTransmissionMenuSync.send(sp);
        }
    }
}
