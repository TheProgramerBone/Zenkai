package com.hmc.zenkai.worldgen.placement;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType;

/**
 * "¿Cae este punto cerca de un sitio donde puede salir una estructura propia?" — para que la
 * decoración del suelo (terrazas, pozas, géiseres, pinchos...) no se solape con ellas.
 *
 * POR QUÉ HACE FALTA: las features de paso LAKES corren ANTES que las estructuras de
 * SURFACE_STRUCTURES, y la estructura decide su sitio mirando el terreno base (sin features). Resultado
 * visto en juego (2026-10-04): una terraza de Pamukkale pegada al capullo de Freezer, con el
 * prado y el árbol montados sobre el borde de la poza. Las features no pueden consultar las
 * estructuras (el StructureManager no se les pasa), pero la colocación random_spread es una
 * función pura de (semilla, región, salt): se recalcula aquí el mismo chunk candidato y se descartan
 * las features cercanas, haya acabado o no generándose la estructura (el coste es perder algo de
 * decoración alrededor de un sitio fallido, a cambio de no depender del orden de generación).
 *
 * Los números SON los de data/zenkai/worldgen/structure_set/*.json: si se cambian allí, cambiarlos aquí.
 */
public final class StructureSites {
    private StructureSites() {}

    /** keepClear: distancia (bloques, Chebyshev) al ancla bajo la cual no se decora. */
    private record Site(RandomSpreadStructurePlacement placement, int spacing, int keepClear) {}

    /** Capullo de Freezer: spacing 40, separation 16, salt 20261002. Despeje: media caja de la
     *  estructura (13) más margen para la feature más grande de su bioma (la terraza, ~10). */
    private static final Site FRIEZA_COCOON = new Site(
            new RandomSpreadStructurePlacement(40, 16, RandomSpreadType.LINEAR, 20261002), 40, 30);
    /** Torre de Janemba: spacing 20, separation 8, salt 20261004 (los de la antigua Compuerta).
     *  Despeje mayor: el pie de la torre con sus pinchos mide ~14 de radio y las features del
     *  Janemba Rift se recentran en su chunk (hasta ~11 del punto de colocación) y alcanzan ~20.
     *  Además deja un claro alrededor del landmark, que la referencia pide (zonas abiertas). */
    private static final Site JANEMBA_TOWER = new Site(
            new RandomSpreadStructurePlacement(20, 8, RandomSpreadType.LINEAR, 20261004), 20, 45);

    public static boolean isNearStructureSite(long seed, BlockPos pos) {
        return near(FRIEZA_COCOON, seed, pos) || near(JANEMBA_TOWER, seed, pos);
    }

    private static boolean near(Site site, long seed, BlockPos pos) {
        int cx = pos.getX() >> 4;
        int cz = pos.getZ() >> 4;
        // La región del punto y las 8 vecinas: el chunk candidato puede estar en la de al lado.
        for (int rx = -1; rx <= 1; rx++) {
            for (int rz = -1; rz <= 1; rz++) {
                var chunk = site.placement().getPotentialStructureChunk(seed,
                        cx + rx * site.spacing(), cz + rz * site.spacing());
                int ax = chunk.getMinBlockX() + 8;
                int az = chunk.getMinBlockZ() + 8;
                if (Math.abs(ax - pos.getX()) < site.keepClear() && Math.abs(az - pos.getZ()) < site.keepClear()) return true;
            }
        }
        return false;
    }
}
