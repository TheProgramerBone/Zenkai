package com.hmc.zenkai.worldgen;

import com.hmc.zenkai.registry.ModBlocks;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Escala "micro" del Janemba Rift: la vegetación de este bioma no es vegetación. Grupos de
 * protuberancias pequeñas sobre el suelo (bultos de carne de 1-2, púas de piedra de 2-4 que se
 * afinan). Rompen el suelo entre las formaciones grandes, pero con mesura: a 1/1 y con burbujas de
 * un bloque (primera versión) el suelo se veía cargado y las burbujas, cubos (capturas del
 * 2026-10-05); la referencia deja zonas abiertas. Frecuencia en ModPlacedFeatures.
 *
 * Siempre a escala de bloque y solo sobre el suelo propio del bioma (nunca encima de una loma azul o
 * de una aguja). Sin recentrar: el grupo cabe en un radio de 5 alrededor del origen.
 */
public class JanembaNubFeature extends Feature<NoneFeatureConfiguration> {

    private static final int GROUND_SEARCH_RADIUS = 4;

    public JanembaNubFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        WorldGenLevel level = ctx.level();
        RandomSource random = ctx.random();
        BlockPos origin = ctx.origin();
        BlockState flesh = ModBlocks.HFIL_CORRUPTED_FLESH.get().defaultBlockState();
        BlockState stone = ModBlocks.HFIL_VOID_STONE.get().defaultBlockState();

        // Dos pasadas: primero el suelo de cada protuberancia, luego los bloques. Con una sola, una
        // púa podía caer en la columna de otra ya colocada y apilarse encima (el mismo bug que hacía
        // trepar a las crestas, ver JanembaSpineRidgeFeature). Siempre desde la Y del origen, sin
        // encadenar alturas.
        int n = 3 + random.nextInt(6);
        int[][] spots = new int[n][];
        for (int i = 0; i < n; i++) {
            int x = origin.getX() + random.nextInt(11) - 5;
            int z = origin.getZ() + random.nextInt(11) - 5;
            int gy = LocalGroundProbe.findGroundY(level, x, origin.getY(), z, GROUND_SEARCH_RADIUS);
            if (gy == Integer.MIN_VALUE) continue;
            if (!JanembaShapes.isBiomeGround(level.getBlockState(new BlockPos(x, gy - 1, z)))) continue;
            spots[i] = new int[]{x, gy, z};
        }
        int placed = 0;
        for (int[] spot : spots) {
            if (spot == null) continue;
            int x = spot[0], gy = spot[1], z = spot[2];
            if (!level.getBlockState(new BlockPos(x, gy, z)).isAir()) continue;
            int kind = random.nextInt(9);
            if (kind < 5) {
                // Bulto de carne: 1 bloque, a veces con un segundo pegado al lado.
                set(level, x, gy, z, flesh);
                if (random.nextBoolean()) {
                    int ox = random.nextBoolean() ? 1 : 0;
                    set(level, x + ox, gy + (random.nextInt(3) == 0 ? 1 : 0), z + (1 - ox), flesh);
                }
            } else {
                // Púa: columna de piedra que se afina (2-4); la base, a veces, de dos bloques.
                int h = 2 + random.nextInt(3);
                for (int k = 0; k < h; k++) set(level, x, gy + k, z, stone);
                if (h >= 3 && random.nextBoolean()) set(level, x + (random.nextBoolean() ? 1 : -1), gy, z, stone);
            }
            placed++;
        }
        return placed > 0;
    }

    private static void set(WorldGenLevel level, int x, int y, int z, BlockState state) {
        BlockPos p = new BlockPos(x, y, z);
        if (level.getBlockState(p).isAir()) level.setBlock(p, state, JanembaShapes.FLAGS);
    }
}
