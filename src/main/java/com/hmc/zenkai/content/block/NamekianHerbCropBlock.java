package com.hmc.zenkai.content.block;

import com.hmc.zenkai.registry.ModItems;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;

/**
 * Hierba medicinal de Namek. Cuatro etapas en vez de las ocho del trigo: es una planta
 * silvestre recolectable, no un cultivo de granja, y con cuatro se distinguen bien.
 */
public class NamekianHerbCropBlock extends CropBlock {
    public static final MapCodec<NamekianHerbCropBlock> CODEC = simpleCodec(NamekianHerbCropBlock::new);

    public static final int MAX_AGE = 3;
    public static final IntegerProperty AGE = BlockStateProperties.AGE_3;

    private static final VoxelShape[] SHAPES = new VoxelShape[]{
            Block.box(0, 0, 0, 16, 4,  16),
            Block.box(0, 0, 0, 16, 7,  16),
            Block.box(0, 0, 0, 16, 11, 16),
            Block.box(0, 0, 0, 16, 15, 16)
    };

    public NamekianHerbCropBlock(Properties properties) {
        super(properties);
    }

    @Override
    public int getMaxAge() { return MAX_AGE; }

    @Override
    protected @NotNull IntegerProperty getAgeProperty() { return AGE; }

    @Override
    protected @NotNull ItemLike getBaseSeedId() { return ModItems.NAMEKIAN_HERB_SEEDS.get(); }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AGE);
    }

    @Override
    protected @NotNull VoxelShape getShape(BlockState state, @NotNull BlockGetter level,
                                           @NotNull BlockPos pos, @NotNull CollisionContext ctx) {
        return SHAPES[state.getValue(AGE)];
    }

    /** Crece más despacio que el trigo: es medicinal, no un cultivo de subsistencia. */
    @Override
    protected int getBonemealAgeIncrease(@NotNull net.minecraft.world.level.Level level) {
        return RandomSource.create().nextInt(1, 2);
    }
}