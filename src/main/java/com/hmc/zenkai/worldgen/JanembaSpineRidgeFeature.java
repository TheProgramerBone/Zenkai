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
 * Crestas de pinchos del Janemba Rift: las hileras de dientes que recortan el horizonte de la
 * referencia de Fusion Reborn, como el lomo de algo enorme. Un lomo bajo que sigue una curva
 * (bezier cuadrática) y, encima, pinchos cada 4-6 bloques que crecen hacia el centro de la cresta
 * y se inclinan SIEMPRE hacia el mismo lado (en la referencia se leen como una sola forma, no como
 * pinchos sueltos).
 *
 * El suelo de cada paso se mide ANTES de colocar nada (ver place), así que la cresta sigue el
 * relieve sin trepar por sí misma. Recentrada
 * en su chunk ({@link JanembaShapes#centered}): con media longitud ≤ 15 cabe en la región
 * escribible.
 */
public class JanembaSpineRidgeFeature extends Feature<NoneFeatureConfiguration> {

    private static final int MIN_LENGTH = 18;
    private static final int MAX_LENGTH = 30;
    private static final int GROUND_SEARCH_RADIUS = 6;
    private static final int MAX_RISE = 8;

    public JanembaSpineRidgeFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        WorldGenLevel level = ctx.level();
        RandomSource random = ctx.random();
        BlockPos origin = JanembaShapes.centered(ctx.origin(), random, 2);

        int g0 = LocalGroundProbe.findGroundY(level, origin.getX(), origin.getY(), origin.getZ(), GROUND_SEARCH_RADIUS);
        if (g0 == Integer.MIN_VALUE) return false;
        if (!JanembaShapes.isBiomeGround(level.getBlockState(new BlockPos(origin.getX(), g0 - 1, origin.getZ())))) return false;

        int length = MIN_LENGTH + random.nextInt(MAX_LENGTH - MIN_LENGTH + 1);
        double angle = random.nextDouble() * Math.PI * 2;
        double ux = Math.cos(angle), uz = Math.sin(angle);        // a lo largo
        double px = -uz, pz = ux;                                  // perpendicular
        // Curvatura de la cresta (≤ 6 hacia un lado): sumada a la inclinación de los pinchos más
        // altos (~13 hacia el mismo lado) es lo que cabe en la región escribible.
        double bend = (random.nextDouble() - 0.5) * length * 0.4;
        double half = length / 2.0;
        // Hacia dónde se inclinan los pinchos: a un lado de la cresta, algo hacia delante.
        double side = random.nextBoolean() ? 1 : -1;
        double lean = 0.2 + random.nextDouble() * 0.2;
        double tipDx = px * side * lean + ux * 0.15, tipDz = pz * side * lean + uz * 0.15;
        // Pincho más alto: 7..12, gruesos y poco inclinados. Con 9..17, finos e inclinados (versión
        // anterior), en juego se leían como troncos secos en paralelo (capturas del 2026-10-05); con
        // 6..13 y finos se perdían entre las lomas. Lo que los hace leerse es el grosor, no la altura.
        double peak = 7 + random.nextInt(6);

        JanembaShapes.Sink sink = JanembaShapes.of(level);
        BlockState stone = ModBlocks.HFIL_VOID_STONE.get().defaultBlockState();
        BlockState flesh = ModBlocks.HFIL_CORRUPTED_FLESH.get().defaultBlockState();

        // DOS PASADAS: primero el suelo de cada paso, luego los bloques. BUG VISTO EN JUEGO
        // (2026-10-05): con una sola pasada, cada paso sondeaba el suelo partiendo del anterior y
        // encontraba el LOMO que se acababa de colocar (la bola de radio 1.4 tapa la columna
        // siguiente), así que la cresta trepaba ~3 bloques por paso y salía como un poste diagonal
        // altísimo con bultos de carne: las "agujas finas como troncos secos" de las capturas.
        int steps = (int) (length / 0.5) + 1;
        double[] px0 = new double[steps], pz0 = new double[steps];
        int[] ground = new int[steps];
        int y = g0;
        for (int i = 0; i < steps; i++) {
            double s = i * 0.5;
            double t = s / length;
            // Bezier cuadrática: de -half a +half a lo largo, con el punto medio desplazado.
            double along = -half + s;
            double off = bend * 4 * t * (1 - t);
            px0[i] = origin.getX() + 0.5 + ux * along + px * off;
            pz0[i] = origin.getZ() + 0.5 + uz * along + pz * off;
            int gy = LocalGroundProbe.findGroundY(level, (int) Math.floor(px0[i]), y, (int) Math.floor(pz0[i]), GROUND_SEARCH_RADIUS);
            // Además, nunca más de MAX_RISE por encima del arranque: es una cresta, no una rampa.
            if (gy != Integer.MIN_VALUE && Math.abs(gy - g0) <= MAX_RISE) y = gy;
            ground[i] = gy == Integer.MIN_VALUE || Math.abs(gy - g0) > MAX_RISE ? Integer.MIN_VALUE : gy;
        }

        int spikes = 0;
        double nextSpike = 2 + random.nextDouble() * 2;
        for (int i = 0; i < steps; i++) {
            int gy = ground[i];
            if (gy == Integer.MIN_VALUE) continue;
            double s = i * 0.5;
            double t = s / length;
            double x = px0[i], z = pz0[i];
            double envelope = Math.sin(Math.PI * t);
            // Lomo: 1-4 de alto y más ancho hacia el centro, carne arriba. Es el que da la forma de
            // aleta: con un lomo de 1-3 y radio fijo 1.4 la cresta era casi solo pinchos.
            double ridgeH = 1 + 3 * envelope;
            double ridgeR = 1.4 + 0.6 * envelope;
            for (int dy = -2; dy < ridgeH; dy++) {
                JanembaShapes.ball(sink, x, gy + dy, z, ridgeR, dy + 1 >= ridgeH ? flesh : stone);
            }
            // Pinchos cada 4-6 bloques y con la altura que marca la envolvente, sin azar: la
            // silueta sale como un arco limpio de dientes. Con uno cada 2-3,5 bloques y ±1 de azar
            // (versión anterior) los pinchos gruesos se fundían en una pared de picos irregulares
            // que en juego se leía como ruido (capturas del 2026-10-05).
            if (s >= nextSpike) {
                double len = Math.max(2, peak * (0.3 + 0.7 * envelope));
                JanembaShapes.spike(sink, x, gy + ridgeH - 1, z, tipDx, 1.0, tipDz, len, 1.6 + 0.8 * envelope, stone);
                nextSpike = s + 4 + random.nextDouble() * 2;
                spikes++;
            }
        }
        return spikes > 0;
    }
}
