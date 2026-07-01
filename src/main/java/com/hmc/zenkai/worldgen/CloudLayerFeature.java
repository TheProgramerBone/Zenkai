package com.hmc.zenkai.worldgen;

import com.hmc.zenkai.content.block.ModBlocks;   // ⚠ ajusta al paquete real de tu ModBlocks
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.LegacyRandomSource;

/**
 * Genera un "mar de nubes" de otherworld_cloud con RELIEVE: el grosor y la altura
 * de la nube varían por columna según un ruido suave, en vez de 3 capas planas.
 */
public class CloudLayerFeature extends Feature<NoneFeatureConfiguration> {

    /** Altura base (parte inferior) de la nube. */
    public static final int CLOUD_BASE_Y = 128;
    /** Grosor mínimo y máximo de la nube (en bloques). */
    public static final int MIN_THICKNESS = 2;
    public static final int MAX_THICKNESS = 6;
    /** Cuánto sube/baja la base de la nube con el relieve (amplitud vertical). */
    public static final int BASE_VARIATION = 4;
    /** Escala del ruido: más pequeño = colinas de nube más grandes y suaves. */
    public static final double NOISE_SCALE = 0.03;

    /** Ruido compartido y determinista (misma semilla => mismas nubes). */
    private static SimplexNoise NOISE;
    private static SimplexNoise noise() {
        if (NOISE == null) {
            RandomSource rng = new WorldgenRandom(new LegacyRandomSource(918273645L));
            NOISE = new SimplexNoise(rng);
        }
        return NOISE;
    }

    public CloudLayerFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        WorldGenLevel level = ctx.level();
        BlockPos origin = ctx.origin();

        // Esquina mínima del chunk (redondeo a múltiplo de 16, válido en negativos)
        int cx = origin.getX() & ~15;
        int cz = origin.getZ() & ~15;

        BlockState cloud = ModBlocks.OTHERWORLD_CLOUD.get().defaultBlockState();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        SimplexNoise noise = noise();

        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                int wx = cx + dx;
                int wz = cz + dz;

                // Ruido en [-1,1] -> [0,1]
                double n = (noise.getValue(wx * NOISE_SCALE, wz * NOISE_SCALE) + 1.0) * 0.5;

                // Grosor y desplazamiento vertical según el ruido
                int thickness = MIN_THICKNESS + (int) Math.round(n * (MAX_THICKNESS - MIN_THICKNESS));
                int baseOffset = (int) Math.round((n - 0.5) * 2.0 * BASE_VARIATION);
                int startY = CLOUD_BASE_Y + baseOffset;

                for (int ly = 0; ly < thickness; ly++) {
                    m.set(wx, startY + ly, wz);
                    level.setBlock(m, cloud, 2);
                }
            }
        }
        return true;
    }
}