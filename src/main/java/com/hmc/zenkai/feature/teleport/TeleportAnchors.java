package com.hmc.zenkai.feature.teleport;

import com.hmc.zenkai.feature.player.OtherworldManager;
import com.hmc.zenkai.registry.ModStructureSegments;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;

/**
 * Posición de cada destino CURADO (Overworld/Otherworld) descubrible — pedido explícito del
 * usuario tras la Fase 2: en vez de recordar por jugador "dónde estabas la última vez que
 * pisaste la zona" (lo que hacía la primera versión), cada estructura tiene UN punto de llegada
 * compartido, igual que ya hacen Yemma (`OtherworldManager.OTHERWORLD_SPAWN`) y Kaiosama
 * (`ModStructureSegments.KAIOSAMA_ENTRANCE`) desde siempre. Kami's Palace es la excepción: al
 * ser una estructura VANILLA (worldgen aleatorio, sin base fija en el código), su posición no se
 * conoce de antemano — su punto viene de un marcador de datos puesto A MANO dentro de la propia
 * pieza NBT (Structure Block en modo Data, nombre "kami_lookout"), capturado en generación y
 * resuelto vía {@link StructureAnchors#get}. Revisión posterior a la primera versión de esta
 * clase: antes se fijaba con la posición LITERAL del primer jugador que la descubría (podía caer
 * en el aire o enterrado); ver StructureAnchors para el porqué del cambio y cómo añadir/mover
 * puntos de este tipo.
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

    /** Nombre del marcador de datos (Structure Block, modo Data) dentro de la pieza de Kami que
     *  define este destino. Ver StructureAnchors para la lista completa de nombres en uso. */
    private static final String KAMI_LOOKOUT_ANCHOR = "kami_lookout";

    /** Posición de `dest`, o null si aún no hay una (Kami's Palace antes de generarse con el
     *  marcador puesto, ver StructureAnchors) o si `dest` es HOME (no aplica, ver arriba). */
    public static BlockPos of(MinecraftServer server, TeleportDestination dest) {
        return switch (dest) {
            case HOME -> null;
            case KAMI_PALACE -> StructureAnchors.get(server, KAMI_LOOKOUT_ANCHOR);
            case YEMMA_PALACE -> OtherworldManager.OTHERWORLD_SPAWN;
            case KAIOSAMA_PLANET -> ModStructureSegments.KAIOSAMA_ENTRANCE;
        };
    }
}
