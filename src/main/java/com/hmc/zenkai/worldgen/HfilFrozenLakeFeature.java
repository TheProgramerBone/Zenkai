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
 * Lago de sangre congelada — pieza central de hfil_frozen_lake (propuesta D de
 * .claude/docs/worldgen.md): el Blood Pond, helado. Referencias: el infierno de
 * hielo de Dragon Ball GT y el Guren ("loto rojo"), uno de los infiernos fríos budistas, donde el
 * frío agrieta la piel como pétalos rojos.
 *
 * Reutiliza la cubeta del charco de sangre (HfilBloodPoolFeature.carve, lámina a una sola altura)
 * con HFIL_BLOOD_ICE en lugar de agua: el lago es una superficie plana de hielo rojo, y la orilla
 * es el suelo del propio bioma (regla del usuario). Más grande que el charco de sangre: es el
 * paisaje del bioma, no un detalle.
 */
public class HfilFrozenLakeFeature extends Feature<NoneFeatureConfiguration> {

    private static final int MIN_RADIUS = 4;
    private static final int MAX_RADIUS = 7;
    private static final int MAX_DEPTH = 2;
    private static final int GROUND_SEARCH_RADIUS = 4;
    private static final float EDGE_ERODE_CHANCE = 0.3f;

    public HfilFrozenLakeFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        WorldGenLevel level = ctx.level();
        BlockPos origin = ctx.origin();
        RandomSource random = ctx.random();

        int groundY = LocalGroundProbe.findGroundY(level, origin.getX(), origin.getY(), origin.getZ(), GROUND_SEARCH_RADIUS);
        if (groundY == Integer.MIN_VALUE) return false;
        BlockState bank = level.getBlockState(new BlockPos(origin.getX(), groundY - 1, origin.getZ()));

        int radius = MIN_RADIUS + random.nextInt(MAX_RADIUS - MIN_RADIUS + 1);
        return HfilBloodPoolFeature.carve(level, random, origin, radius, MAX_DEPTH,
                GROUND_SEARCH_RADIUS, EDGE_ERODE_CHANCE, null,
                ModBlocks.HFIL_BLOOD_ICE.get().defaultBlockState(), bank);
    }
}
