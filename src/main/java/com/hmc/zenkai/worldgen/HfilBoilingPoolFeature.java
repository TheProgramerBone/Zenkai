package com.hmc.zenkai.worldgen;

import com.hmc.zenkai.registry.ModFluids;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Poza de agua hirviendo azul cobalto ("Umi Jigoku", el infierno del mar de Beppu) — propuesta A
 * de .claude/docs/worldgen.md. Reutiliza la cubeta abierta del charco de sangre
 * (HfilBloodPoolFeature.carve) con otro relleno. Más pequeña y menos honda que el charco de
 * sangre: es una poza, no un lago.
 *
 * LA ORILLA ES EL SUELO DEL PROPIO BIOMA (decisión del usuario, 2026-10-02): se lee el bloque de
 * suelo bajo el centro y se usa ese mismo para la orilla. La primera versión ponía siempre piedra
 * pálida (las costras minerales blancas de las fuentes reales), y fuera del suelo blanco de
 * needle_wastes se veía como un parche de otro color que "no debería estar ahí". El agua azul sí
 * se queda: es el concepto de la poza, no un bloque de construcción.
 */
public class HfilBoilingPoolFeature extends Feature<NoneFeatureConfiguration> {

    private static final int MIN_RADIUS = 2;
    private static final int MAX_RADIUS = 4;
    private static final int MAX_DEPTH = 2;
    private static final int GROUND_SEARCH_RADIUS = 4;
    private static final float EDGE_ERODE_CHANCE = 0.3f;

    public HfilBoilingPoolFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        WorldGenLevel level = ctx.level();
        BlockPos origin = ctx.origin();
        RandomSource random = ctx.random();

        int groundY = LocalGroundProbe.findGroundY(level, origin.getX(), origin.getY(), origin.getZ(), GROUND_SEARCH_RADIUS);
        if (groundY == Integer.MIN_VALUE) return false;
        BlockState bank = level.getBlockState(new BlockPos(origin.getX(), groundY - 1, origin.getZ()));

        int radius = MIN_RADIUS + random.nextInt(MAX_RADIUS - MIN_RADIUS + 1);
        return HfilBloodPoolFeature.carve(level, random, origin, radius, MAX_DEPTH,
                GROUND_SEARCH_RADIUS, EDGE_ERODE_CHANCE, null,
                ModFluids.HFIL_BOILING_WATER.get().defaultFluidState().createLegacyBlock(), bank);
    }
}
