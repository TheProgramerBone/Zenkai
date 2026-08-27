package com.hmc.zenkai.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Sondeo de suelo LOCAL, acotado a una ventana pequeña alrededor de una Y de referencia — en vez
 * de un heightmap global (Heightmap.Types.*), que siempre devuelve el bloque sólido MÁS ALTO de
 * TODA la columna. Eso tiene dos problemas para decoración del suelo del HFIL/Otherworld:
 *
 * 1) Una columna puede tener el suelo del HFIL abajo Y una isla flotante mucho más arriba (ver
 *    ClampedHeightmapPlacement) — un heightmap global capturaría la altura de la ISLA en vez del
 *    suelo real si la columna cae bajo una.
 * 2) Las variantes "_WG" quedan CONGELADAS antes de que corra cualquier feature de decoración —
 *    si otra feature ya modificó el terreno de esa columna en el mismo chunk, el heightmap sigue
 *    reportando la altura VIEJA.
 *
 * Al mirar solo una ventana pequeña alrededor de una Y ya conocida (el origen del placement, o el
 * de un elemento hermano ya colocado), ninguno de los dos problemas puede afectar el resultado —
 * una isla a y=150 o un heightmap desactualizado quedan fuera de la ventana. Mismo principio que
 * ya usa FallenLogFeature.groundIsFlatEnough (ese caso compara una huella entera contra una Y de
 * origen fija en vez de buscar una Y por columna, así que no comparte código con esto, pero es la
 * misma familia de arreglo). Usado por HfilSpikeFeature, HfilBonePileFeature y
 * HfilBloodPoolFeature — público porque BloodPondPiece (worldgen.structure, Fase 6 del rework
 * del HFIL) también lo necesita para plantar sus marcadores/cartel al nivel de suelo real.
 */
public final class LocalGroundProbe {
    private LocalGroundProbe() {}

    /**
     * Devuelve la Y del primer bloque sólido no líquido escaneando hacia abajo desde
     * {@code baseY + searchRadius}, sin bajar de {@code baseY - searchRadius}, o
     * {@link Integer#MIN_VALUE} si no hay ninguno dentro de la ventana.
     */
    public static int findGroundY(WorldGenLevel level, int x, int baseY, int z, int searchRadius) {
        int top = Math.min(baseY + searchRadius, level.getMaxBuildHeight() - 1);
        int bottom = Math.max(baseY - searchRadius, level.getMinBuildHeight());
        for (int y = top; y >= bottom; y--) {
            BlockState state = level.getBlockState(new BlockPos(x, y, z));
            if (!state.isAir() && state.getFluidState().isEmpty()) return y + 1;
        }
        return Integer.MIN_VALUE;
    }
}
