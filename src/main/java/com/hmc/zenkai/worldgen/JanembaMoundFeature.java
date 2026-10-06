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
 * Ondulaciones del Janemba Rift: lo que quita la planitud del suelo púrpura de la referencia
 * (colinas suaves, superficies onduladas) SIN tocar la función de densidad del Otherworld, que
 * comparten los cinco biomas del HFIL y cuyo riesgo está documentado en docs/worldgen.md.
 *
 * Una loma con joroba: dos campanas de coseno solapadas (una principal y otra menor desplazada),
 * cada una elíptica y girada, así que nunca sale una semiesfera reconocible. Cada columna se
 * levanta desde SU suelo (sigue el relieve) y se rellena con el material de su propia superficie:
 * en la franja rosa crece carne, en la violeta piedra (regla del usuario: construir con el suelo
 * real). Solo sobre suelo propio del bioma; es la primera feature del bioma, el resto se apoya
 * en ella.
 */
public class JanembaMoundFeature extends Feature<NoneFeatureConfiguration> {

    private static final int GROUND_SEARCH_RADIUS = 5;

    public JanembaMoundFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    private record Bell(double cx, double cz, double a, double b, double cos, double sin, double h) {
        double height(double x, double z) {
            double dx = x - cx, dz = z - cz;
            double u = dx * cos + dz * sin, v = -dx * sin + dz * cos;
            double e = (u * u) / (a * a) + (v * v) / (b * b);
            if (e >= 1.0) return 0;
            return h * (0.5 + 0.5 * Math.cos(Math.PI * Math.sqrt(e)));
        }
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        WorldGenLevel level = ctx.level();
        RandomSource random = ctx.random();
        // Alcance máximo ~20 (joroba incluida): con holgura 3 cabe en los 24 que hay desde el centro.
        BlockPos origin = JanembaShapes.centered(ctx.origin(), random, 3);

        int g0 = LocalGroundProbe.findGroundY(level, origin.getX(), origin.getY(), origin.getZ(), GROUND_SEARCH_RADIUS);
        if (g0 == Integer.MIN_VALUE) return false;
        if (!JanembaShapes.isBiomeGround(level.getBlockState(new BlockPos(origin.getX(), g0 - 1, origin.getZ())))) return false;

        double angle = random.nextDouble() * Math.PI;
        double cos = Math.cos(angle), sin = Math.sin(angle);
        // Lomas bajas y anchas. Con 6..14 de semieje y 2..7 de alto en cada chunk (primera versión),
        // cada loma dejaba 5-6 anillos escalonados muy marcados y el suelo se veía cargado
        // (capturas del 2026-10-05). Menos alto = menos escalones; más ancho = pendiente suave.
        double a = 9 + random.nextInt(6);                 // semieje largo 9..14
        double b = a * (0.6 + random.nextDouble() * 0.35);
        double h = 1 + random.nextInt(4);                 // 1..4
        Bell main = new Bell(origin.getX(), origin.getZ(), a, b, cos, sin, h);
        double off = a * (0.45 + random.nextDouble() * 0.3) * (random.nextBoolean() ? 1 : -1);
        double angle2 = angle + (random.nextDouble() - 0.5) * 1.2;
        Bell hump = new Bell(origin.getX() + cos * off, origin.getZ() + sin * off,
                a * 0.65, b * 0.75, Math.cos(angle2), Math.sin(angle2), h * (0.45 + random.nextDouble() * 0.45));

        BlockState voidStone = ModBlocks.HFIL_VOID_STONE.get().defaultBlockState();
        int reach = (int) Math.ceil(a + Math.abs(off)) + 1;
        int placed = 0;
        for (int x = origin.getX() - reach; x <= origin.getX() + reach; x++) {
            for (int z = origin.getZ() - reach; z <= origin.getZ() + reach; z++) {
                double hc = Math.max(main.height(x + 0.5, z + 0.5), hump.height(x + 0.5, z + 0.5));
                int add = (int) Math.round(hc);
                if (add <= 0) continue;
                int gy = LocalGroundProbe.findGroundY(level, x, g0, z, GROUND_SEARCH_RADIUS);
                if (gy == Integer.MIN_VALUE) continue;
                BlockState surface = level.getBlockState(new BlockPos(x, gy - 1, z));
                if (!JanembaShapes.isBiomeGround(surface)) continue;
                if (gy + add >= level.getMaxBuildHeight() - 1) continue;
                // El suelo viejo pasa a ser cuerpo; la superficie sube con la loma.
                level.setBlock(new BlockPos(x, gy - 1, z), voidStone, JanembaShapes.FLAGS);
                for (int i = 0; i < add - 1; i++) {
                    level.setBlock(new BlockPos(x, gy + i, z), voidStone, JanembaShapes.FLAGS);
                }
                level.setBlock(new BlockPos(x, gy + add - 1, z), surface, JanembaShapes.FLAGS);
                placed++;
            }
        }
        return placed > 0;
    }
}
