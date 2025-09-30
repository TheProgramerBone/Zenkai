package com.hmc.db_renewed.block.special;

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
}