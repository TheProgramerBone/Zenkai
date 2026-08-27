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
 * Formaciones de pinchos del HFIL — altas y dramáticas, pensadas para leerse como silueta de
 * horizonte, réplica del contraste frío/cálido que se ve en las 4 imágenes de referencia de la
 * sesión de diseño (siluetas de pico azul-violeta contra el cielo rojo/rosa del HFIL). Feature
 * ligera, sin jigsaw ni structure_set — mismo estilo que FallenLogFeature/CloudLayerFeature,
 * eso es solo para estructuras grandes tipo dragon ball.
 *
 * Un solo placement genera un CLÚSTER de 1-3 pinchos (no un cono solitario): las referencias
 * muestran varias agujas de altura desigual agrupadas, no una sola torre uniforme.
 */
public class HfilSpikeFeature extends Feature<NoneFeatureConfiguration> {

    private static final int MIN_HEIGHT = 15;
    private static final int MAX_HEIGHT = 36;
    private static final int MIN_BASE_RADIUS = 2;
    private static final int MAX_BASE_RADIUS = 4;
    private static final int MIN_SPIKES = 1;
    private static final int MAX_SPIKES = 3;
    /** Cuánto se puede alejar cada pincho del origen del clúster, en bloques (radio del área). */
    private static final int CLUSTER_SPREAD = 5;
    /** Ventana de sondeo local para encontrar el suelo real de CADA pincho — ver LocalGroundProbe
     *  para el porqué (no un heightmap global). */
    private static final int GROUND_SEARCH_RADIUS = 4;
    /** Probabilidad de "erosionar" un bloque del borde de cada capa — la silueta perfectamente
     *  circular de un cono liso no se parece a las referencias (cristales angulosos, fracturados). */
    private static final float EDGE_ERODE_CHANCE = 0.35f;

    public HfilSpikeFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        WorldGenLevel level = ctx.level();
        BlockPos origin = ctx.origin();
        RandomSource random = ctx.random();

        int spikeCount = MIN_SPIKES + random.nextInt(MAX_SPIKES - MIN_SPIKES + 1);
        boolean placedAny = false;

        for (int i = 0; i < spikeCount; i++) {
            int dx = random.nextInt(CLUSTER_SPREAD * 2 + 1) - CLUSTER_SPREAD;
            int dz = random.nextInt(CLUSTER_SPREAD * 2 + 1) - CLUSTER_SPREAD;
            int x = origin.getX() + dx;
            int z = origin.getZ() + dz;

            int groundY = LocalGroundProbe.findGroundY(level, x, origin.getY(), z, GROUND_SEARCH_RADIUS);
            if (groundY == Integer.MIN_VALUE) continue; // sin suelo real cerca: no se coloca este pincho

            placeSpike(level, random, x, groundY, z);
            placedAny = true;
        }
        return placedAny;
    }

    private static void placeSpike(WorldGenLevel level, RandomSource random, int baseX, int baseY, int baseZ) {
        int height = MIN_HEIGHT + random.nextInt(MAX_HEIGHT - MIN_HEIGHT + 1);
        int baseRadius = MIN_BASE_RADIUS + random.nextInt(MAX_BASE_RADIUS - MIN_BASE_RADIUS + 1);
        int maxY = level.getMaxBuildHeight() - 1;
        BlockState rock = ModBlocks.HFIL_SPIKE_ROCK.get().defaultBlockState();

        for (int dy = 0; dy < height; dy++) {
            int y = baseY + dy;
            if (y > maxY) break;

            // Taper lineal de baseRadius (en la base) a 0 (en la punta), con algo de ruido por
            // capa para romper la silueta de cono perfecto — las referencias son cristales
            // angulosos, no estalagmitas lisas.
            double t = dy / (double) height;
            double radius = baseRadius * (1.0 - t) + (random.nextDouble() - 0.5);
            int r = (int) Math.round(Math.max(radius, 0));

            if (r <= 0) {
                level.setBlock(new BlockPos(baseX, y, baseZ), rock, Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS);
                continue;
            }

            int rSq = r * r;
            int innerSq = (r - 1) * (r - 1);
            for (int ddx = -r; ddx <= r; ddx++) {
                for (int ddz = -r; ddz <= r; ddz++) {
                    int distSq = ddx * ddx + ddz * ddz;
                    if (distSq > rSq) continue; // fuera de la sección circular de esta capa
                    boolean edge = distSq > innerSq;
                    if (edge && random.nextFloat() < EDGE_ERODE_CHANCE) continue; // erosión del borde
                    level.setBlock(new BlockPos(baseX + ddx, y, baseZ + ddz), rock,
                            Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS);
                }
            }
        }
    }
}
