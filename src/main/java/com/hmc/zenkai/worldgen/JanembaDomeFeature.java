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
 * Masas azules del Janemba Rift: "grandes estructuras, formas curvas y masivas" de la referencia
 * de Fusion Reborn, el contraste azul contra el suelo púrpura. Dos variantes de la misma forma:
 * <ul>
 *   <li><b>Lomo</b> (2 de cada 3): semielipsoide bajo y alargado, una ballena azul que asoma.</li>
 *   <li><b>Ola</b>: más alta y estrecha, y cada capa se desplaza a lo largo de su eje cuanto más
 *   arriba está, así que la cima cuelga sobre un lado como la gran masa curva del borde derecho
 *   de la referencia.</li>
 * </ul>
 * Se construye por capas: cada capa es la elipse de la base encogida (perfil de semielipsoide) y,
 * en la ola, desplazada. Con el bloque propio de masa azul, no con hormigón plano (la primera
 * versión se leía como "lana azul").
 *
 * Anclada a suelo real y parejo; recentrada en su chunk para que la ola entera quepa en la región
 * escribible (alcance máximo ~22).
 */
public class JanembaDomeFeature extends Feature<NoneFeatureConfiguration> {

    private static final int GROUND_SEARCH_RADIUS = 5;
    private static final int MAX_SLOPE = 3;

    public JanembaDomeFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        WorldGenLevel level = ctx.level();
        RandomSource random = ctx.random();
        BlockPos origin = JanembaShapes.centered(ctx.origin(), random, 2);

        boolean wave = random.nextInt(3) == 0;
        int a = wave ? 8 + random.nextInt(5) : 7 + random.nextInt(9);           // semieje largo
        int b = Math.max(3, (int) (a * (wave ? 0.35 + random.nextDouble() * 0.15 : 0.45 + random.nextDouble() * 0.25)));
        int h = wave ? (int) (a * (1.3 + random.nextDouble() * 0.5)) : Math.max(4, (int) (a * (0.5 + random.nextDouble() * 0.45)));
        double lean = wave ? 0.55 + random.nextDouble() * 0.3 : 0.0;           // fracción de a
        double angle = random.nextDouble() * Math.PI * 2;
        double cos = Math.cos(angle), sin = Math.sin(angle);

        int g0 = LocalGroundProbe.findGroundY(level, origin.getX(), origin.getY(), origin.getZ(), GROUND_SEARCH_RADIUS);
        if (g0 == Integer.MIN_VALUE) return false;
        if (!JanembaShapes.isBiomeGround(level.getBlockState(new BlockPos(origin.getX(), g0 - 1, origin.getZ())))) return false;
        for (int s = -1; s <= 1; s += 2) {
            int gx = origin.getX() + (int) Math.round(cos * a * 0.8 * s);
            int gz = origin.getZ() + (int) Math.round(sin * a * 0.8 * s);
            int g = LocalGroundProbe.findGroundY(level, gx, g0, gz, GROUND_SEARCH_RADIUS);
            if (g == Integer.MIN_VALUE || Math.abs(g - g0) > MAX_SLOPE) return false;
        }
        if (g0 + h > level.getMaxBuildHeight() - 2) return false;

        BlockState blue = ModBlocks.HFIL_JANEMBA_BLUE_MASS.get().defaultBlockState();
        int reach = (int) Math.ceil(a * (1 + lean)) + 1;
        for (int y = -2; y <= h; y++) {
            double t = Math.max(0, y) / (double) h;
            double scale = Math.sqrt(Math.max(0, 1.0 - t * t));
            if (scale < 0.12) break;
            double shift = lean * a * t * t;   // la ola se vuelca a lo largo de su eje
            double sa = a * scale, sb = b * scale;
            for (int dx = -reach; dx <= reach; dx++) {
                for (int dz = -reach; dz <= reach; dz++) {
                    double u = dx * cos + dz * sin - shift;
                    double v = -dx * sin + dz * cos;
                    if ((u * u) / (sa * sa) + (v * v) / (sb * sb) > 1.0) continue;
                    level.setBlock(new BlockPos(origin.getX() + dx, g0 + y, origin.getZ() + dz), blue, JanembaShapes.FLAGS);
                }
            }
        }
        return true;
    }
}
