package com.hmc.db_renewed.content.blockentity.DragonBalls;

import com.hmc.db_renewed.content.block.ModBlocks;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SpreadingSnowyDirtBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Supplier;

public class NamekianGrassBlock extends SpreadingSnowyDirtBlock {
    public static final MapCodec<NamekianGrassBlock> CODEC =
            simpleCodec(props -> new NamekianGrassBlock(props, () -> Blocks.DIRT));

    private final Supplier<Block> dirtBlock;

    public NamekianGrassBlock(Properties props, Supplier<Block> dirtBlock) {
        super(props.randomTicks());
        this.dirtBlock = dirtBlock;
    }

    @Override
    protected MapCodec<? extends SpreadingSnowyDirtBlock> codec() {
        return CODEC;
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource rng) {
        if (!canStayGrass(level, pos)) {
            level.setBlockAndUpdate(pos, dirtBlock.get().defaultBlockState());
        }

        if (level.getMaxLocalRawBrightness(pos.above()) >= 9) {
            // Intenta unas cuantas veces por tick (4 es el número típico vanilla)
            for (int i = 0; i < 4; ++i) {
                BlockPos target = pos.offset(
                        rng.nextInt(3) - 1,     // x: -1..1
                        rng.nextInt(5) - 3,     // y: -3..1 (algo hacia abajo también)
                        rng.nextInt(3) - 1      // z: -1..1
                );

                if (canSpreadTo(level, target)) {
                    level.setBlockAndUpdate(target, this.defaultBlockState());
                }
            }
        }
    }

    private static boolean canStayGrass(LevelReader level, BlockPos pos) {
        BlockPos abovePos = pos.above();
        BlockState above = level.getBlockState(abovePos);

        if (above.getFluidState().isSource()) return false;

        int opacity = above.getLightBlock(level, abovePos);
        if (opacity >= level.getMaxLightLevel()) return false;

        int brightness = level.getMaxLocalRawBrightness(abovePos);
        return brightness >= 4;
    }

    private boolean canSpreadTo(LevelReader level, BlockPos targetPos) {
        if (!level.getBlockState(targetPos).is(ModBlocks.NAMEKIAN_DIRT.get())) return false;

        BlockPos abovePos = targetPos.above();
        BlockState above = level.getBlockState(abovePos);

        if (above.getFluidState().isSource()) return false;

        if (above.getLightBlock(level, abovePos) >= level.getMaxLightLevel()) return false;

        return level.getMaxLocalRawBrightness(abovePos) >= 4;
    }
}