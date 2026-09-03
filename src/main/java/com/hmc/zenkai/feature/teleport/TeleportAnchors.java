package com.hmc.zenkai.feature.teleport;

import com.hmc.zenkai.feature.player.OtherworldManager;
import com.hmc.zenkai.registry.ModStructureSegments;
import com.hmc.zenkai.worldgen.ZenkaiWorldData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;

/**
 * Posición de cada destino CURADO (Overworld/Otherworld) descubrible — pedido explícito del
 * usuario tras la Fase 2: en vez de recordar por jugador "dónde estabas la última vez que
 * pisaste la zona" (lo que hacía la primera versión), cada estructura tiene UN punto de llegada
 * compartido, igual que ya hacen Yemma (`OtherworldManager.OTHERWORLD_SPAWN`) y Kaiosama
 * (`ModStructureSegments.KAIOSAMA_ENTRANCE`) desde siempre. Kami's Palace es la excepción: al
 * ser una estructura VANILLA (worldgen aleatorio, sin base fija en el código), su posición no se
 * conoce de antemano — se FIJA la primera vez que alguien la descubre (TeleportDiscoverySystem)
 * y queda guardada server-wide en ZenkaiWorldData para siempre, igual que el resto de
 * estructuras únicas del mod ya guardan su posición ahí (ver ZenkaiStructurePlacement).
 * HOME no vive aquí: no es una estructura del mundo, se resuelve del respawn del propio
 * jugador (ver TeleportRequestPacket.resolveHome).
 * Nether/End/cualquier dimensión GENÉRICA (de un mod de terceros) ya NO pasan por aquí: su
 * posición es siempre POR JUGADOR (InstantTransmissionAttachment.lastEntryPos, ver
 * DimensionEntryTracker) y se resuelve directamente en GenericDimensionTeleportPacket.handle,
 * sin necesitar ningún caso especial en este switch — antes NETHER_PORTAL/END_SPAWN sí vivían
 * aquí, se retiraron al generalizar el sistema.
 */
public final class TeleportAnchors {
    private TeleportAnchors() {}

    /** Clave bajo la que se guarda la posición fijada de Kami's Palace en ZenkaiWorldData. */
    private static final String KAMI_PALACE_POS_KEY = "kami_palace_anchor";

    /** Posición de `dest`, o null si aún no hay una (Kami's Palace antes de su primer
     *  descubrimiento por cualquiera) o si `dest` es HOME (no aplica, ver arriba). */
    public static BlockPos of(MinecraftServer server, TeleportDestination dest) {
        return switch (dest) {
            case HOME -> null;
            case KAMI_PALACE -> ZenkaiWorldData.get(server).getPos(KAMI_PALACE_POS_KEY);
            case YEMMA_PALACE -> OtherworldManager.OTHERWORLD_SPAWN;
            case KAIOSAMA_PLANET -> ModStructureSegments.KAIOSAMA_ENTRANCE;
        };
    }

    /** Fija la posición de Kami's Palace la primera vez que se descubre. No hace nada las
     *  veces siguientes (ni si ya la fijó otro jugador antes) — es un punto FIJO, no se
     *  actualiza con cada nueva visita. */
    public static void fixKamiPalaceIfNeeded(MinecraftServer server, BlockPos pos) {
        ZenkaiWorldData data = ZenkaiWorldData.get(server);
        if (data.getPos(KAMI_PALACE_POS_KEY) == null) {
            data.setPos(KAMI_PALACE_POS_KEY, pos);
        }
    }
}
