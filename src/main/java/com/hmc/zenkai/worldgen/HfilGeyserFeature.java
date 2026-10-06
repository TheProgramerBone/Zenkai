package com.hmc.zenkai.worldgen;

import com.hmc.zenkai.content.block.HfilGeyserBlock;
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
 * Géiser ("Tatsumaki Jigoku" de Beppu) — propuesta A de .claude/pendiente/worldgen-pendiente.md.
 * Una boca (HfilGeyserBlock) a ras de suelo con un pequeño cono de cráter alrededor.
 *
 * HECHO CON EL SUELO DEL BIOMA, por completo (decisión del usuario, 2026-10-02): la primera versión ponía una
 * costra de piedra pálida (sinter blanco) en los tres biomas, y en los dos que no son blancos se
 * leía como un parche de otro color que "no debería estar ahí". Ahora la boca toma la variante
 * GROUND del bloque que sustituye y el cono se hace con ese MISMO bloque: el géiser se distingue
 * por su forma y su agujero, no por un color ajeno.
 *
 * En worldgen onPlace NO corre, así que el ciclo de erupción se programa aquí a mano
 * (scheduleTick funciona en WorldGenLevel: va a los ticks del protochunk, igual que los
 * manantiales vanilla). Si se perdiera, randomTick del bloque lo recupera.
 */
public class HfilGeyserFeature extends Feature<NoneFeatureConfiguration> {

    private static final int GROUND_SEARCH_RADIUS = 4;
    /** Probabilidad de cada bloque del cono: los 4 lados casi siempre, las esquinas a veces —
     *  un anillo irregular de 1 de alto, no un cuadrado perfecto. */
    private static final float CONE_SIDE_CHANCE = 0.7f;
    private static final float CONE_CORNER_CHANCE = 0.35f;

    public HfilGeyserFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        WorldGenLevel level = ctx.level();
        BlockPos origin = ctx.origin();
        RandomSource random = ctx.random();

        int groundY = LocalGroundProbe.findGroundY(level, origin.getX(), origin.getY(), origin.getZ(), GROUND_SEARCH_RADIUS);
        if (groundY == Integer.MIN_VALUE) return false;
        BlockPos vent = new BlockPos(origin.getX(), groundY - 1, origin.getZ());
        // Sin cielo libre encima (bajo un saliente, dentro de un charco) un géiser no tiene sentido.
        for (int dy = 1; dy <= 3; dy++) {
            if (!level.getBlockState(vent.above(dy)).isAir()) return false;
        }

        // El suelo que se sustituye decide de qué está hecho el géiser (ver javadoc).
        BlockState ground = level.getBlockState(vent);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                boolean corner = dx != 0 && dz != 0;
                if (random.nextFloat() >= (corner ? CONE_CORNER_CHANCE : CONE_SIDE_CHANCE)) continue;
                int x = vent.getX() + dx;
                int z = vent.getZ() + dz;
                // Solo sobre suelo a la misma altura que la boca: en un desnivel, el cono
                // flotaría o se enterraría.
                if (LocalGroundProbe.findGroundY(level, x, groundY, z, 1) != groundY) continue;
                level.setBlock(new BlockPos(x, groundY, z), ground, Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS);
            }
        }

        Block geyser = ModBlocks.HFIL_GEYSER.get();
        level.setBlock(vent, geyser.defaultBlockState()
                        .setValue(HfilGeyserBlock.GROUND, HfilGeyserBlock.groundFor(ground)),
                Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS);
        HfilGeyserBlock.scheduleIdle(level, vent, geyser, random);
        return true;
    }
}
