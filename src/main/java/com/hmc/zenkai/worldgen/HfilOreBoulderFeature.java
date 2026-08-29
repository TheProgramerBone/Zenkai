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
 * Pequeño afloramiento de Katchin visible en superficie del HFIL — DELIBERADO, a diferencia del
 * accidente de que una veta subterránea (KATCHIN_ORE_HFIL) llegue por casualidad a la superficie
 * de blood_shore porque su surface_rule no tapa la roca con tierra (ver otherworld_noise.json).
 * Mismo estilo ligero que HfilBonePileFeature/HfilSpikeFeature: sin jigsaw, sondeo de suelo LOCAL
 * vía LocalGroundProbe, bloques colocados a mano. A diferencia de un Feature.ORE normal (que
 * SUSTITUYE roca ya existente bajo tierra), esto AÑADE un montículo pequeño de Katchin asentado
 * SOBRE el suelo real, como una roca que sobresale — nunca se entierra.
 * Registrado dos veces con distinta rareza (ver ModPlacedFeatures): frecuente en blood_shore (que
 * ya tiene identidad minera fuerte con la veta expuesta) y raro en needle_wastes/cinder_dunes (su
 * primer gancho minero propio).
 */
public class HfilOreBoulderFeature extends Feature<NoneFeatureConfiguration> {

    private static final int MIN_ORE = 2;
    private static final int MAX_ORE = 4;
    /** Cuánto se puede alejar cada bloque extra del primero, en bloques. */
    private static final int BOULDER_SPREAD = 1;
    /** Ventana de sondeo local del suelo real — ver LocalGroundProbe para el porqué (no un
     *  heightmap global). */
    private static final int GROUND_SEARCH_RADIUS = 3;
    /** Probabilidad de apilar una segunda capa sobre cada bloque colocado, para que el conjunto
     *  se lea como una roca con volumen y no como una baldosa plana. */
    private static final float STACK_CHANCE = 0.4f;

    public HfilOreBoulderFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        WorldGenLevel level = ctx.level();
        BlockPos origin = ctx.origin();
        RandomSource random = ctx.random();

        int originGroundY = LocalGroundProbe.findGroundY(level, origin.getX(), origin.getY(), origin.getZ(), GROUND_SEARCH_RADIUS);
        if (originGroundY == Integer.MIN_VALUE) return false; // sin suelo real bajo el origen

        BlockState ore = ModBlocks.KATCHIN_ORE.get().defaultBlockState();
        int oreCount = MIN_ORE + random.nextInt(MAX_ORE - MIN_ORE + 1);
        boolean placedAny = false;

        // El primer bloque siempre va en el origen exacto (garantiza un afloramiento real, no
        // solo vecinos dispersos que podrían fallar todos); el resto se dispersa alrededor.
        for (int i = 0; i < oreCount; i++) {
            int dx = i == 0 ? 0 : random.nextInt(BOULDER_SPREAD * 2 + 1) - BOULDER_SPREAD;
            int dz = i == 0 ? 0 : random.nextInt(BOULDER_SPREAD * 2 + 1) - BOULDER_SPREAD;
            int x = origin.getX() + dx;
            int z = origin.getZ() + dz;

            int y = i == 0 ? originGroundY : LocalGroundProbe.findGroundY(level, x, originGroundY, z, GROUND_SEARCH_RADIUS);
            if (y == Integer.MIN_VALUE) continue;

            BlockPos pos = new BlockPos(x, y, z);
            if (!level.getBlockState(pos).isAir()) continue;
            level.setBlock(pos, ore, Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS);
            placedAny = true;

            if (random.nextFloat() < STACK_CHANCE) {
                BlockPos above = pos.above();
                if (level.getBlockState(above).isAir()) {
                    level.setBlock(above, ore, Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS);
                }
            }
        }
        return placedAny;
    }
}
