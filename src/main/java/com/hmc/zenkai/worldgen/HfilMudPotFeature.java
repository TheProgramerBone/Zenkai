package com.hmc.zenkai.worldgen;

import com.hmc.zenkai.registry.ModBlocks;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Charca de barro gris hirviendo ("Oniishibozu Jigoku" de Beppu) — propuesta A de
 * .claude/docs/worldgen.md. No cava una cubeta: sustituye la capa de suelo por
 * HFIL_BOILING_MUD en un disco irregular (el barro ya se hunde solo, ver HfilBoilingMudBlock),
 * con una segunda capa en el centro para que no sea una lámina de 1 bloque.
 */
public class HfilMudPotFeature extends Feature<NoneFeatureConfiguration> {

    private static final int MIN_RADIUS = 2;
    private static final int MAX_RADIUS = 3;
    private static final int GROUND_SEARCH_RADIUS = 4;
    private static final float EDGE_ERODE_CHANCE = 0.35f;

    public HfilMudPotFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        WorldGenLevel level = ctx.level();
        BlockPos origin = ctx.origin();
        RandomSource random = ctx.random();
        int radius = MIN_RADIUS + random.nextInt(MAX_RADIUS - MIN_RADIUS + 1);
        BlockState mud = ModBlocks.HFIL_BOILING_MUD.get().defaultBlockState();

        int centerY = LocalGroundProbe.findGroundY(level, origin.getX(), origin.getY(), origin.getZ(), GROUND_SEARCH_RADIUS);
        if (centerY == Integer.MIN_VALUE) return false;

        boolean placedAny = false;
        int rSq = radius * radius;
        int innerSq = (radius - 1) * (radius - 1);
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int distSq = dx * dx + dz * dz;
                if (distSq > rSq) continue;
                if (distSq > innerSq && random.nextFloat() < EDGE_ERODE_CHANCE) continue;

                int x = origin.getX() + dx;
                int z = origin.getZ() + dz;
                int groundY = LocalGroundProbe.findGroundY(level, x, centerY, z, GROUND_SEARCH_RADIUS);
                if (groundY == Integer.MIN_VALUE) continue;
                // Solo suelo seco: no convertir en barro el fondo de un charco.
                if (!level.getBlockState(new BlockPos(x, groundY, z)).isAir()) continue;

                level.setBlock(new BlockPos(x, groundY - 1, z), mud, Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS);
                if (distSq <= 1) {
                    level.setBlock(new BlockPos(x, groundY - 2, z), mud, Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS);
                }
                placedAny = true;
            }
        }
        return placedAny;
    }
}
