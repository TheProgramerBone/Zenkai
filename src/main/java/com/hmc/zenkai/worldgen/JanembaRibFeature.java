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
 * Arcos orgánicos del Janemba Rift: las "estructuras orgánicas columnares y curvas" de la
 * referencia de Fusion Reborn. Salen del suelo y vuelven a entrar a 10-24 bloques, gruesos en los
 * pies y finos en lo alto, y además se TUERCEN hacia un lado (la cima no queda en el plano de los
 * dos pies): un arco recto se leía como un puente, no como algo que creció.
 *
 * Los dos pies se anclan por separado con LocalGroundProbe y se exige suelo sólido bajo cada uno
 * (la lección de las agujas flotantes de rocky_wasteland: sin eso, el arco cuelga de una cornisa).
 * Si los pies difieren más de {@link #MAX_FOOT_DELTA} de altura, se descarta: sería un arco cojo.
 */
public class JanembaRibFeature extends Feature<NoneFeatureConfiguration> {

    private static final int MIN_SPAN = 10;
    private static final int MAX_SPAN = 24;
    private static final int MIN_RISE = 7;
    private static final int MAX_RISE = 18;
    private static final int GROUND_SEARCH_RADIUS = 6;
    private static final int MIN_SOLID_DEPTH = 3;
    private static final int MAX_FOOT_DELTA = 5;
    private static final float FLESH_CHANCE = 0.12f;

    public JanembaRibFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        WorldGenLevel level = ctx.level();
        RandomSource random = ctx.random();
        BlockPos origin = JanembaShapes.centered(ctx.origin(), random, 3);

        int span = MIN_SPAN + random.nextInt(MAX_SPAN - MIN_SPAN + 1);
        double angle = random.nextDouble() * Math.PI * 2;
        int dx = (int) Math.round(Math.cos(angle) * span / 2.0);
        int dz = (int) Math.round(Math.sin(angle) * span / 2.0);

        int ax = origin.getX() - dx, az = origin.getZ() - dz;
        int bx = origin.getX() + dx, bz = origin.getZ() + dz;
        int ay = LocalGroundProbe.findGroundY(level, ax, origin.getY(), az, GROUND_SEARCH_RADIUS);
        int by = LocalGroundProbe.findGroundY(level, bx, origin.getY(), bz, GROUND_SEARCH_RADIUS);
        if (ay == Integer.MIN_VALUE || by == Integer.MIN_VALUE) return false;
        if (Math.abs(ay - by) > MAX_FOOT_DELTA) return false;
        if (!solidFooting(level, ax, ay, az) || !solidFooting(level, bx, by, bz)) return false;

        int rise = MIN_RISE + random.nextInt(MAX_RISE - MIN_RISE + 1);
        if (Math.max(ay, by) + rise + 3 > level.getMaxBuildHeight() - 3) return false;
        // Torsión: la cima se aparta del plano de los pies hacia un lado.
        double twist = (2 + random.nextDouble() * 3) * (random.nextBoolean() ? 1 : -1);
        double px = -Math.sin(angle), pz = Math.cos(angle);
        BlockState stone = ModBlocks.HFIL_VOID_STONE.get().defaultBlockState();
        BlockState flesh = ModBlocks.HFIL_CORRUPTED_FLESH.get().defaultBlockState();
        JanembaShapes.Sink sink = JanembaShapes.of(level);

        int steps = span * 3;
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            double arch = Math.sin(t * Math.PI);
            double cx = ax + (bx - ax) * t + px * twist * arch + 0.5;
            double cz = az + (bz - az) * t + pz * twist * arch + 0.5;
            double base = ay + (by - ay) * t - 2; // se entierra 2 bloques en cada pie
            double cy = base + (rise + 2) * arch;
            double radius = 1.9 - 0.9 * arch;     // grueso en los pies, fino arriba
            JanembaShapes.ball(sink, cx, cy, cz, radius, random.nextFloat() < FLESH_CHANCE ? flesh : stone);
        }
        return true;
    }

    private static boolean solidFooting(WorldGenLevel level, int x, int groundY, int z) {
        for (int i = 1; i <= MIN_SOLID_DEPTH; i++) {
            BlockState s = level.getBlockState(new BlockPos(x, groundY - i, z));
            if (s.isAir() || !s.getFluidState().isEmpty()) return false;
        }
        return true;
    }
}
