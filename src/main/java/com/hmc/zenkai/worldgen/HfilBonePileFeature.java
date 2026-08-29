package com.hmc.zenkai.worldgen;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Huesos/cráneos dispersos del HFIL (rediseño "infierno de Dragon Ball", ver
 * .claude/pendiente/hfil-rework-propuesta.md, punto 5) — las almas que no lograron cruzar
 * Snake Way. Decoración puntual barata, mismo estilo ligero que FallenLogFeature/
 * HfilSpikeFeature: sin jigsaw ni structure_set (eso es solo para estructuras grandes tipo
 * dragon ball). A diferencia de FallenLogFeature no hace falta un StructureTemplate NBT — un
 * puñado de minecraft:bone_block con eje aleatorio ya da variedad de sobra por sí solo, así que
 * se coloca a mano, bloque a bloque, igual que HfilSpikeFeature.
 */
public class HfilBonePileFeature extends Feature<NoneFeatureConfiguration> {

    private static final int MIN_BONES = 3;
    private static final int MAX_BONES = 7;
    /** Radio del área en la que se dispersan los huesos del montón. */
    private static final int PILE_SPREAD = 2;
    /** Ventana de sondeo local para encontrar el suelo real de CADA hueso — ver
     *  LocalGroundProbe para el porqué (no un heightmap global). */
    private static final int GROUND_SEARCH_RADIUS = 3;
    /** Probabilidad de coronar el montón con una calavera. */
    private static final float SKULL_CHANCE = 0.4f;

    public HfilBonePileFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        WorldGenLevel level = ctx.level();
        BlockPos origin = ctx.origin();
        RandomSource random = ctx.random();

        int boneCount = MIN_BONES + random.nextInt(MAX_BONES - MIN_BONES + 1);
        BlockPos topBonePos = null;
        boolean placedAny = false;

        for (int i = 0; i < boneCount; i++) {
            int dx = random.nextInt(PILE_SPREAD * 2 + 1) - PILE_SPREAD;
            int dz = random.nextInt(PILE_SPREAD * 2 + 1) - PILE_SPREAD;
            int x = origin.getX() + dx;
            int z = origin.getZ() + dz;

            int groundY = LocalGroundProbe.findGroundY(level, x, origin.getY(), z, GROUND_SEARCH_RADIUS);
            if (groundY == Integer.MIN_VALUE) continue; // sin suelo real cerca: se salta este hueso

            BlockPos bonePos = new BlockPos(x, groundY, z);
            if (!level.getBlockState(bonePos).isAir()) continue; // el hueco donde iría ya está ocupado

            Direction.Axis axis = Direction.Axis.values()[random.nextInt(3)];
            BlockState bone = Blocks.BONE_BLOCK.defaultBlockState().setValue(RotatedPillarBlock.AXIS, axis);
            level.setBlock(bonePos, bone, Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS);
            placedAny = true;

            // La calavera corona el hueso más alto colocado, no uno cualquiera — se decide al
            // final del bucle, aquí solo se recuerda el candidato más alto visto hasta ahora.
            if (topBonePos == null || bonePos.getY() > topBonePos.getY()) topBonePos = bonePos;
        }

        if (placedAny && topBonePos != null && random.nextFloat() < SKULL_CHANCE) {
            BlockPos skullPos = topBonePos.above();
            if (level.getBlockState(skullPos).isAir()) {
                BlockState skull = Blocks.SKELETON_SKULL.defaultBlockState()
                        .setValue(SkullBlock.ROTATION, random.nextInt(16));
                level.setBlock(skullPos, skull, Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS);
            }
        }

        return placedAny;
    }
}
