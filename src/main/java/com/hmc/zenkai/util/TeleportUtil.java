package com.hmc.zenkai.util;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Teletransporte seguro. NO existía ninguna utilidad de este tipo en el mod antes de la
 * Transmisión Instantánea (ver CLAUDE.md — OtherworldManager/HtcTravel solo usan coordenadas
 * fijas hardcodeadas sin comprobar nada), así que esto es de uso general para cualquier
 * teletransporte futuro que necesite evitar asfixiar al jugador dentro de un bloque sólido,
 * no solo la Fase 1 de Instant Transmission.
 * Deliberadamente simple: busca un hueco de aire de 2 bloques con algo sólido/vacío alrededor,
 * sin replicar la complejidad completa de PortalForcer.findValidSpawn (no hay portal que
 * orientar, solo "no aparecer dentro de pared").
 */
public final class TeleportUtil {
    private TeleportUtil() {}

    /** Radio de búsqueda (en bloques) alrededor del punto candidato. */
    private static final int SEARCH_RADIUS = 2;

    /**
     * Punto seguro más cercano a `target`: primero el propio punto, luego capas por encima/
     * debajo, y por último un barrido horizontal en la misma Y. Si nada en el radio sirve,
     * devuelve `target` tal cual — mejor aparecer un instante embutido que no llegar en absoluto.
     */
    public static BlockPos findSafeSpot(ServerLevel level, BlockPos target) {
        if (isSafe(level, target)) return target;

        for (int dy = 1; dy <= SEARCH_RADIUS; dy++) {
            BlockPos up = target.above(dy);
            if (isSafe(level, up)) return up;
            BlockPos down = target.below(dy);
            if (isSafe(level, down)) return down;
        }

        for (int dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx++) {
            for (int dz = -SEARCH_RADIUS; dz <= SEARCH_RADIUS; dz++) {
                if (dx == 0 && dz == 0) continue;
                BlockPos p = target.offset(dx, 0, dz);
                if (isSafe(level, p)) return p;
            }
        }
        return target;
    }

    /** Sin colisión en pos ni en el bloque de encima (hueco de 2 de alto para el jugador), y
     *  sin ningún portal real pegado — ver nearPortal. */
    private static boolean isSafe(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()
                && !nearPortal(level, pos);
    }

    /** ¿Hay un bloque de portal (Nether o End) en `pos` o en cualquiera de sus 26 vecinos
     *  inmediatos (cubo 3×3×3 centrado en `pos`)? Aterrizar DENTRO o pegado a un portal real
     *  dispara el cruce de vainilla otra vez de inmediato — el punto que graba
     *  DimensionEntryTracker es justo donde vainilla deja al jugador tras cruzar un portal del
     *  Nether, que por diseño está pegado al propio portal (bug real reportado por el usuario:
     *  usar el Portal del Nether de Instant Transmission devolvía al Overworld al instante).
     *  Comprobar solo el bloque exacto del objetivo no basta — hay que descartar también sus
     *  vecinos para que la búsqueda de findSafeSpot se aleje de verdad 1-2 bloques del portal,
     *  no que se quede pegado a su borde. */
    private static boolean nearPortal(ServerLevel level, BlockPos pos) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (isPortalBlock(level, pos.offset(dx, dy, dz))) return true;
                }
            }
        }
        return false;
    }

    private static boolean isPortalBlock(ServerLevel level, BlockPos pos) {
        Block block = level.getBlockState(pos).getBlock();
        return block == Blocks.NETHER_PORTAL || block == Blocks.END_PORTAL || block == Blocks.END_GATEWAY;
    }

    /** Centro del bloque en X/Z; el Y se deja tal cual (los pies del jugador van ahí, igual
     *  que cualquier BlockPos pasado a Entity#teleportTo). */
    public static Vec3 footCenter(BlockPos pos) {
        return new Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
    }
}
