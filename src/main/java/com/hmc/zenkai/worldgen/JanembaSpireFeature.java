package com.hmc.zenkai.worldgen;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Agujas orgánicas del Janemba Rift: la escala "grande" de las torres de la referencia (las del
 * fondo, más bajas y sin corona). Geometría en {@link JanembaTowerShape}.
 *
 * Exige suelo parejo bajo el pie (una aguja colgando de una ladera se ve rota) y suelo propio del
 * bioma: no nace encima de otra formación.
 */
public class JanembaSpireFeature extends Feature<NoneFeatureConfiguration> {

    private static final int MIN_HEIGHT = 13;
    private static final int MAX_HEIGHT = 24;
    private static final int GROUND_SEARCH_RADIUS = 6;
    private static final int MAX_SLOPE = 3;

    public JanembaSpireFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        WorldGenLevel level = ctx.level();
        RandomSource random = ctx.random();
        BlockPos origin = JanembaShapes.centered(ctx.origin(), random, 3);

        int g0 = LocalGroundProbe.findGroundY(level, origin.getX(), origin.getY(), origin.getZ(), GROUND_SEARCH_RADIUS);
        if (g0 == Integer.MIN_VALUE) return false;
        if (!JanembaShapes.isBiomeGround(level.getBlockState(new BlockPos(origin.getX(), g0 - 1, origin.getZ())))) return false;
        for (int[] d : new int[][]{{3, 0}, {-3, 0}, {0, 3}, {0, -3}}) {
            int g = LocalGroundProbe.findGroundY(level, origin.getX() + d[0], g0, origin.getZ() + d[1], GROUND_SEARCH_RADIUS);
            if (g == Integer.MIN_VALUE || Math.abs(g - g0) > MAX_SLOPE) return false;
        }

        var params = new JanembaTowerShape.Params(
                MIN_HEIGHT + random.nextInt(MAX_HEIGHT - MIN_HEIGHT + 1),
                // Base gruesa y poca inclinación: con radio 2.2-3.3 e inclinación hasta 4.5 se
                // leían como troncos secos más que como torres (capturas del 2026-10-05).
                3.0 + random.nextDouble() * 1.2,
                1.0 + random.nextDouble() * 1.5,
                false);
        if (g0 + JanembaTowerShape.top(params) > level.getMaxBuildHeight() - 2) return false;
        JanembaTowerShape.build(JanembaShapes.of(level), random.nextLong(), origin.getX(), g0, origin.getZ(), params);
        return true;
    }
}
