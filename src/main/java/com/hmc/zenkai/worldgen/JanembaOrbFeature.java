package com.hmc.zenkai.worldgen;

import com.hmc.zenkai.content.block.JanembaBubbleBlock;
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
 * Esferas de colores del Janemba Rift: lo más reconocible de la referencia de Fusion Reborn
 * (.claude/pendiente/referencia_janemba.png), burbujas brillantes de gelatina de muchos tamaños y
 * colores repartidas por el aire. Solo bloques (decisión del usuario, 2026-10-05: sin render de
 * cliente ni entidades).
 *
 * Cada colocación es un GRUPO de 1-3 esferas de tamaños distintos, separadas y a alturas
 * distintas: unas zonas quedan llenas y otras abiertas, en vez de una esfera igual por chunk.
 * Tamaños por escala: pequeñas (r 2-3) las más frecuentes, medianas (r 4-5) y grandes (r 6-7)
 * raras. Alguna grande o mediana queda posada y medio hundida en el suelo, como la burbuja rosa
 * del primer plano de la referencia.
 *
 * Cáscara de un bloque de {@link JanembaBubbleBlock} (gelatina sin marco: la esfera se lee como
 * una sola superficie), con un parche de reflejo ({@link JanembaBubbleBlock#SHINE}) en la parte de
 * arriba hacia la que mira la "luz" de la escena.
 */
public class JanembaOrbFeature extends Feature<NoneFeatureConfiguration> {

    private static final int GROUND_SEARCH_RADIUS = 6;
    /** Dirección de la luz del reflejo (arriba y hacia un lado, normalizada al usarla). */
    private static final double LX = -0.45, LY = 0.8, LZ = -0.4;
    private static final double SHINE_DOT = 0.88;

    public JanembaOrbFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        WorldGenLevel level = ctx.level();
        RandomSource random = ctx.random();
        BlockPos origin = JanembaShapes.centered(ctx.origin(), random, 3);

        // Grupos de 1-2 (a veces 3), una vez cada 3 chunks (ModPlacedFeatures): menos de la mitad de
        // esferas que la primera versión (1-4 cada 2 chunks), que en juego tapaba el cielo
        // (capturas del 2026-10-05). La referencia deja mucho cielo entre esfera y esfera.
        int n = 1 + random.nextInt(2) + (random.nextInt(6) == 0 ? 1 : 0);
        int placed = 0;
        for (int i = 0; i < n; i++) {
            int r;
            int roll = random.nextInt(100);
            // Radio mínimo 2: con r 1 la "esfera" son 18 bloques en cruz y en juego se leía como un
            // cubo (capturas del 2026-10-05). Sesgado a las pequeñas: las grandes (antes 10 %, hasta
            // r 8) ocupaban media pantalla; ahora 4 % y hasta r 7.
            if (roll < 72) r = 2 + random.nextInt(2);
            else if (roll < 96) r = 4 + random.nextInt(2);
            else r = 6 + random.nextInt(2);
            // Separadas dentro del grupo; holgura 3 + 10 + r ≤ 21 cabe en los 24 desde el centro del chunk.
            int x = origin.getX() + random.nextInt(21) - 10;
            int z = origin.getZ() + random.nextInt(21) - 10;
            if (placeOne(level, random, x, origin.getY(), z, r)) placed++;
        }
        return placed > 0;
    }

    private static boolean placeOne(WorldGenLevel level, RandomSource random, int x, int baseY, int z, int r) {
        int groundY = LocalGroundProbe.findGroundY(level, x, baseY, z, GROUND_SEARCH_RADIUS);
        if (groundY == Integer.MIN_VALUE) return false;
        if (!level.getFluidState(new BlockPos(x, groundY - 1, z)).isEmpty()) return false;

        boolean grounded = r >= 4 && random.nextInt(6) == 0;
        int cy;
        if (grounded) {
            cy = groundY + (int) Math.round(r * 0.35);
        } else {
            int minAbove = r <= 3 ? 6 : (r <= 5 ? 10 : 16);
            int maxAbove = r <= 3 ? 40 : (r <= 5 ? 50 : 58);
            cy = groundY + minAbove + r + random.nextInt(maxAbove - minAbove + 1);
        }
        if (cy + r + 1 > level.getMaxBuildHeight() - 2) return false;
        if (!grounded && !airAround(level, x, cy, z, r)) return false;

        // Solo la paleta de la referencia: los otros 9 colores de tinte son para el jugador.
        var colors = JanembaBubbleBlock.WORLDGEN_COLORS;
        JanembaBubbleBlock.Color color = colors.get(random.nextInt(colors.size()));
        BlockState shell = ModBlocks.HFIL_JANEMBA_BUBBLE.get().defaultBlockState()
                .setValue(JanembaBubbleBlock.COLOR, color);
        BlockState shine = shell.setValue(JanembaBubbleBlock.SHINE, true);

        double ln = Math.sqrt(LX * LX + LY * LY + LZ * LZ);
        double outer = (r + 0.5) * (r + 0.5);
        double inner = (r - 0.5) * (r - 0.5);
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    double d2 = dx * dx + dy * dy + dz * dz;
                    if (d2 > outer || d2 < inner) continue; // solo la cáscara
                    BlockPos p = new BlockPos(x + dx, cy + dy, z + dz);
                    BlockState here = level.getBlockState(p);
                    // Posada en el suelo: solo sustituye aire o el suelo del bioma, nunca otra formación.
                    if (!here.isAir() && !(grounded && JanembaShapes.isBiomeGround(here))) continue;
                    double dot = (dx * LX + dy * LY + dz * LZ) / (Math.sqrt(d2) * ln);
                    level.setBlock(p, dot > SHINE_DOT ? shine : shell, JanembaShapes.FLAGS);
                }
            }
        }
        return true;
    }

    /** Aire en el eje y en un anillo de la esfera: no incrustarla en una isla ni en otra formación. */
    private static boolean airAround(WorldGenLevel level, int x, int cy, int z, int r) {
        for (int dy = -r; dy <= r; dy += Math.max(1, r / 2)) {
            for (int[] d : new int[][]{{0, 0}, {r, 0}, {-r, 0}, {0, r}, {0, -r}}) {
                if (!level.getBlockState(new BlockPos(x + d[0], cy + dy, z + d[1])).isAir()) return false;
            }
        }
        return true;
    }
}
