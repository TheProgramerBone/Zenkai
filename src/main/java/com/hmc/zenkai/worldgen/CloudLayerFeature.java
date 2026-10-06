package com.hmc.zenkai.worldgen;

import com.hmc.zenkai.registry.ModBlocks;   // ⚠ ajusta al paquete real de tu ModBlocks
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseRouter;
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

        double[][] rift = riftDistance(level, cx, cz);

        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                int wx = cx + dx;
                int wz = cz + dz;

                // Ruido en [-1,1] -> [0,1]
                double n = (noise.getValue(wx * NOISE_SCALE, wz * NOISE_SCALE) + 1.0) * 0.5;

                // Grosor y desplazamiento vertical según el ruido
                int thickness = MIN_THICKNESS + (int) Math.round(n * (MAX_THICKNESS - MIN_THICKNESS));

                // Desgarro sobre el Janemba Rift (ver riftDistance): hueco dentro, jirones en el borde.
                if (rift != null) {
                    double d = bilinear(rift, dx, dz);
                    TearNoise tn = tear();
                    double e = d - RIFT_INSET
                            + TEAR_WIDE * tn.wide.getValue(wx * 0.06, wz * 0.06)
                            + TEAR_FINE * tn.fine.getValue(wx * 0.22, wz * 0.22);
                    if (e > 0) continue;
                    if (e > -RIFT_FRAY) {
                        double keep = -e / RIFT_FRAY;   // 0 en el filo del hueco, 1 al salir del deshilachado
                        // Jirones: columnas sueltas que caen, más cuanto más cerca del hueco.
                        if (JanembaShapes.hash01(0x5EA1C10DL, wx, 0, wz) > 0.35 + 0.65 * keep) continue;
                        thickness = Math.max(1, (int) Math.round(thickness * keep));
                    }
                }
                int baseOffset = (int) Math.round((n - 0.5) * 2.0 * BASE_VARIATION);
                int startY = CLOUD_BASE_Y + baseOffset;

                for (int ly = 0; ly < thickness; ly++) {
                    m.set(wx, startY + ly, wz);
                    // Solo aire: la nube NO puede comerse la parte baja de las islas
                    // (la superficie empieza en y130 y la banda de nube llega a y138).
                    if (level.isEmptyBlock(m)) level.setBlock(m, cloud, 2);
                }
            }
        }
        return true;
    }

    // ── Desgarro sobre el Janemba Rift (2026-10-05, petición del usuario) ─────────────────────
    // La grieta de Janemba es "algo que no debería estar ahí": rompe el techo de nubes entre el
    // HFIL y el Otro Mundo. El hueco se puede atravesar volando (aceptado). Las islas de encima
    // las quita otherworld_noise.json (penalización en final_density con el mismo clima), con más
    // margen hacia fuera que este hueco: por el agujero no debe asomar ninguna.

    /** Bloques hacia dentro del borde del bioma donde empieza el hueco. */
    private static final double RIFT_INSET = 6.0;
    /** Ancho del deshilachado (nube que adelgaza y se rompe en jirones) antes del hueco. */
    private static final double RIFT_FRAY = 7.0;
    /** Amplitud en bloques del ruido que desgarra el borde: ondas grandes + dientes finos. */
    private static final double TEAR_WIDE = 6.0, TEAR_FINE = 2.5;
    /** Rango del clima del Janemba Rift en dimension/otherworld.json: humidity &lt; -0.35 y
     *  temperature en [-0.15, 0.4]. Si cambia allí, cambiarlo aquí y en otherworld_noise.json. */
    private static final double HUM_MAX = -0.35, TEMP_MIN = -0.15, TEMP_MAX = 0.4;

    private record TearNoise(SimplexNoise wide, SimplexNoise fine) {}
    private static TearNoise TEAR;
    private static TearNoise tear() {
        if (TEAR == null) {
            TEAR = new TearNoise(new SimplexNoise(new WorldgenRandom(new LegacyRandomSource(5517093L))),
                    new SimplexNoise(new WorldgenRandom(new LegacyRandomSource(7723661L))));
        }
        return TEAR;
    }

    /**
     * Distancia aproximada en BLOQUES al borde del Janemba Rift (positiva dentro) en una rejilla de
     * paso 4 que cubre el chunk (índices 0..4 → offsets 0..16), o null si el chunk queda lejos.
     *
     * Lee el clima del router (lo mismo que decide el bioma) en vez de getBiome: es continuo, así
     * que el borde no sale en escalones de 4 bloques, y dividiendo por su gradiente se obtiene una
     * distancia en bloques, que es lo que hace falta para que el desgarro tenga el mismo ancho en
     * cualquier sitio (el clima cambia muy despacio en unas zonas y rápido en otras).
     */
    private static double[][] riftDistance(WorldGenLevel level, int cx, int cz) {
        NoiseRouter router = level.getLevel().getChunkSource().randomState().router();
        // Rejilla de 7x7 (offsets -4..20) para poder sacar el gradiente en los bordes de la de 5x5.
        double[][] s = new double[7][7];
        double best = -1;
        for (int i = 0; i < 7; i++) {
            for (int k = 0; k < 7; k++) {
                var p = new DensityFunction.SinglePointContext(cx - 4 + i * 4, 0, cz - 4 + k * 4);
                double t = router.temperature().compute(p);
                double h = router.vegetation().compute(p);
                s[i][k] = Math.min(HUM_MAX - h, Math.min(t - TEMP_MIN, TEMP_MAX - t));
                best = Math.max(best, s[i][k]);
            }
        }
        // Margen holgado en unidades de clima: lejos del bioma no se toca la nube.
        if (best < -0.06) return null;
        double[][] d = new double[5][5];
        for (int i = 0; i < 5; i++) {
            for (int k = 0; k < 5; k++) {
                double gx = (s[i + 2][k + 1] - s[i][k + 1]) / 8.0;
                double gz = (s[i + 1][k + 2] - s[i + 1][k]) / 8.0;
                double g = Math.max(Math.sqrt(gx * gx + gz * gz), 1e-5);
                d[i][k] = s[i + 1][k + 1] / g;
            }
        }
        return d;
    }

    private static double bilinear(double[][] g, int dx, int dz) {
        int i = Math.min(dx >> 2, 3), k = Math.min(dz >> 2, 3);
        double fx = (dx - i * 4) / 4.0, fz = (dz - k * 4) / 4.0;
        double a = g[i][k] + (g[i + 1][k] - g[i][k]) * fx;
        double b = g[i][k + 1] + (g[i + 1][k + 1] - g[i][k + 1]) * fx;
        return a + (b - a) * fz;
    }
}