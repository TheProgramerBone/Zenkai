package com.hmc.zenkai.client;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Espejo cliente del estado de Transmisión Instantánea que el HUD y el menú de planetas
 * necesitan. El attachment del servidor NUNCA llega solo al cliente (no es un attachment
 * auto-sincronizado como PlayerStatsAttachment) — todo lo de aquí llega por packets S2C
 * explícitos (InstantTransmissionSyncPacket para cooldown + quietud, InstantTransmissionMenuStatePacket
 * para descubrimiento/dimensiones visitadas). No es un attachment ni necesita persistencia
 * propia, es puro estado de UI que se reconstruye del servidor en cada login.
 */
public final class InstantTransmissionClientState {
    private InstantTransmissionClientState() {}

    private static int cooldownTicks = 0;
    private static int stillTicks = 0;
    private static Set<String> discoveredIds = Set.of();
    private static Set<String> visitedDimensionIds = Set.of();
    private static Set<String> blockedDimensionIds = Set.of();
    private static Set<String> waypointIds = Set.of();

    public static void setCooldownTicks(int ticks) { cooldownTicks = Math.max(0, ticks); }

    public static int cooldownTicks() { return cooldownTicks; }

    public static boolean onCooldown() { return cooldownTicks > 0; }

    /** Ticks de "quieto" acumulados ahora mismo con TAB pulsado (0 si no lo está) — ver
     *  InstantTransmissionCrosshairOverlay, que lo compara contra
     *  InstantTransmissionAttachment.MENU_ARM_TICKS para saber si el ícono de la mira debe
     *  teñirse de "armado". Servidor-autoritativo (InstantTransmissionSystem), igual que el
     *  cooldown: nunca lo calcula el cliente por su cuenta. */
    public static void setStillTicks(int ticks) { stillTicks = Math.max(0, ticks); }

    public static int stillTicks() { return stillTicks; }

    public static void applyMenuState(List<String> discovered, List<String> visitedDimensions, List<String> waypoints) {
        discoveredIds = new HashSet<>(discovered);
        visitedDimensionIds = new HashSet<>(visitedDimensions);
        waypointIds = new HashSet<>(waypoints);
    }

    public static boolean isDiscovered(String destinationId) {
        return discoveredIds.contains(destinationId);
    }

    /** Espejo de InstantTransmissionAttachment.hasWaypoint — para GenericSubDestination.Waypoint,
     *  igual que isDiscovered ya cubre TeleportDestination. */
    public static boolean hasWaypoint(String waypointKey) {
        return waypointIds.contains(waypointKey);
    }

    public static boolean hasVisitedDimension(String dimensionId) {
        return visitedDimensionIds.contains(dimensionId);
    }

    /** Todas las dimensiones visitadas — usado por InstantTransmissionMenuScreen para construir
     *  las filas GENÉRICAS del menú (cualquier dimensión de cualquier mod, ver
     *  GenericDimensionRow), no solo para comprobar una en concreto. */
    public static Set<String> visitedDimensionIdsView() { return Set.copyOf(visitedDimensionIds); }

    /** Ver InstantTransmissionBlocklistSyncPacket — llega en el login y en cada /reload, nunca
     *  en bucle. Una dimensión bloqueada simplemente no debe aparecer en el menú, pase lo que
     *  pase su estado de descubrimiento/visita. */
    public static void applyBlocklist(List<String> blocked) {
        blockedDimensionIds = new HashSet<>(blocked);
    }

    public static boolean isBlocked(String dimensionId) {
        return blockedDimensionIds.contains(dimensionId);
    }
}
