package com.hmc.zenkai.util;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
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

    /** Sin colisión en pos ni en el bloque de encima (hueco de 2 de alto para el jugador). */
    private static boolean isSafe(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty();
    }

    /** Centro del bloque en X/Z; el Y se deja tal cual (los pies del jugador van ahí, igual
     *  que cualquier BlockPos pasado a Entity#teleportTo). */
    public static Vec3 footCenter(BlockPos pos) {
        return new Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
    }
}
