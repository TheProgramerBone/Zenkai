package com.hmc.zenkai.worldgen;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Cráter de gas en llamas — propuesta F de .claude/docs/worldgen.md. Referencia
 * real: el cráter de Darvaza, la "Puerta del Infierno" del desierto de Karakum (Turkmenistán), un
 * pozo de gas que arde sin parar desde hace décadas. Exclusivo de cinder_dunes (el desierto del
 * HFIL).
 *
 * Un cuenco excavado (paraboloide: hondo en el centro, suave hacia el borde). Las paredes no se
 * construyen: son el terreno que queda al excavar, o sea la arena y la piedra del propio bioma
 * (regla del usuario; desde 2026-10-02 el subsuelo de cada bioma es de su piedra). En el fondo,
 * magma (el gas ardiendo, que es el concepto) con fuego encima que NO se apaga: el magma está en
 * #minecraft:infiniburn_overworld, el infiniburn de la dimensión. De noche se ve de lejos.
 */
public class HfilGasCraterFeature extends Feature<NoneFeatureConfiguration> {

    private static final int MIN_RADIUS = 5;
    private static final int MAX_RADIUS = 7;
    private static final int MIN_DEPTH = 4;
    private static final int MAX_DEPTH = 6;
    /** Parte del radio con fondo de magma (el resto del cuenco queda en la roca del bioma). */
    private static final double FIRE_FRACTION = 0.55;
    private static final float FIRE_CHANCE = 0.6f;
    private static final int GROUND_SEARCH_RADIUS = 4;
    /** Desnivel máximo bajo la huella: más y el cuenco quedaría abierto por un lado. */
    private static final int MAX_STEP = 2;

    public HfilGasCraterFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        WorldGenLevel level = ctx.level();
        BlockPos origin = ctx.origin();
        RandomSource random = ctx.random();

        int g0 = LocalGroundProbe.findGroundY(level, origin.getX(), origin.getY(), origin.getZ(), GROUND_SEARCH_RADIUS);
        if (g0 == Integer.MIN_VALUE) return false;
        int radius = MIN_RADIUS + random.nextInt(MAX_RADIUS - MIN_RADIUS + 1);
        int depth = MIN_DEPTH + random.nextInt(MAX_DEPTH - MIN_DEPTH + 1);

        int[][] ground = new int[2 * radius + 1][2 * radius + 1];
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > radius * radius) continue;
                int g = LocalGroundProbe.findGroundY(level, origin.getX() + dx, g0, origin.getZ() + dz, GROUND_SEARCH_RADIUS);
                if (g == Integer.MIN_VALUE || Math.abs(g - g0) > MAX_STEP) return false;
                ground[dx + radius][dz + radius] = g;
            }
        }

        BlockState magma = Blocks.MAGMA_BLOCK.defaultBlockState();
        BlockState fire = Blocks.FIRE.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                double t2 = (dx * dx + dz * dz) / (double) (radius * radius);
                if (t2 > 1.0) continue;
                int x = origin.getX() + dx, z = origin.getZ() + dz;
                int groundTop = ground[dx + radius][dz + radius] - 1;
                // Fondo del cuenco en esta columna (paraboloide), medido desde el suelo del centro.
                int floorY = (g0 - 1) - (int) Math.round(depth * (1.0 - t2));
                for (int y = floorY + 1; y <= groundTop; y++) set(level, x, y, z, air);
                if (Math.sqrt(t2) <= FIRE_FRACTION) {
                    set(level, x, floorY, z, magma);
                    if (random.nextFloat() < FIRE_CHANCE) set(level, x, floorY + 1, z, fire);
                }
            }
        }
        return true;
    }

    private static void set(WorldGenLevel level, int x, int y, int z, BlockState state) {
        if (y < level.getMinBuildHeight()) return;
        level.setBlock(new BlockPos(x, y, z), state, Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS);
    }
}
