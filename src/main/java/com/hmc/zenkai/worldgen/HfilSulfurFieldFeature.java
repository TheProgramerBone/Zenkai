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
 * Campo de azufre — propuesta E de .claude/pendiente/worldgen-pendiente.md. Referencia real:
 * Osorezan (Japón), monte volcánico tenido por la puerta del inframundo budista, con costras de
 * azufre amarillo y fumarolas. Exclusivo de cinder_dunes (el bioma volcánico).
 *
 * Una mancha irregular de costra de azufre que SUSTITUYE la capa de suelo (no se eleva sobre ella:
 * es el suelo cambiando de naturaleza, como en el real), cada vez más rala hacia el borde para que
 * se funda con la ceniza alrededor en vez de cortar en seco; y 1-3 fumarolas dentro, a ras.
 */
public class HfilSulfurFieldFeature extends Feature<NoneFeatureConfiguration> {

    private static final int MIN_RADIUS = 3;
    private static final int MAX_RADIUS = 6;
    private static final int MIN_FUMAROLES = 1;
    private static final int MAX_FUMAROLES = 3;
    private static final int GROUND_SEARCH_RADIUS = 3;

    public HfilSulfurFieldFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        WorldGenLevel level = ctx.level();
        BlockPos origin = ctx.origin();
        RandomSource random = ctx.random();

        int centerY = LocalGroundProbe.findGroundY(level, origin.getX(), origin.getY(), origin.getZ(), GROUND_SEARCH_RADIUS);
        if (centerY == Integer.MIN_VALUE) return false;
        int radius = MIN_RADIUS + random.nextInt(MAX_RADIUS - MIN_RADIUS + 1);
        BlockState crust = ModBlocks.HFIL_SULFUR_CRUST.get().defaultBlockState();

        boolean placedAny = false;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                double t = Math.sqrt(dx * dx + dz * dz) / radius;
                if (t > 1.0) continue;
                // Densa en el centro, rala en el borde: 100 % hasta la mitad, luego cae a ~15 %.
                float keep = t < 0.5 ? 1f : (float) (1.0 - (t - 0.5) * 1.7);
                if (random.nextFloat() > keep) continue;
                BlockPos top = surfaceBlock(level, origin.getX() + dx, centerY, origin.getZ() + dz);
                if (top == null) continue;
                level.setBlock(top, crust, Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS);
                placedAny = true;
            }
        }
        if (!placedAny) return false;

        int fumaroles = MIN_FUMAROLES + random.nextInt(MAX_FUMAROLES - MIN_FUMAROLES + 1);
        BlockState vent = ModBlocks.HFIL_FUMAROLE.get().defaultBlockState();
        for (int i = 0; i < fumaroles; i++) {
            int r = Math.max(1, radius / 2);
            BlockPos top = surfaceBlock(level, origin.getX() + random.nextInt(2 * r + 1) - r, centerY,
                    origin.getZ() + random.nextInt(2 * r + 1) - r);
            if (top != null) level.setBlock(top, vent, Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS);
        }
        return true;
    }

    /** Bloque de suelo de la columna (el último sólido, con aire encima), o null. */
    private static BlockPos surfaceBlock(WorldGenLevel level, int x, int nearY, int z) {
        int g = LocalGroundProbe.findGroundY(level, x, nearY, z, GROUND_SEARCH_RADIUS);
        if (g == Integer.MIN_VALUE || !level.getBlockState(new BlockPos(x, g, z)).isAir()) return null;
        return new BlockPos(x, g - 1, z);
    }
}
