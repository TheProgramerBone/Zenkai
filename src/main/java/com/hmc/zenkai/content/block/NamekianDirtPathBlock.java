package com.hmc.zenkai.content.block;

import com.hmc.zenkai.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;

/**
 * Camino de tierra namekiano.
 * No extiende DirtPathBlock a propósito: ese tiene cableado Blocks.DIRT en su conversión,
 * así que al taparlo con un bloque sólido se volvería tierra vanilla en mitad de Namek.
 * Aquí la conversión apunta a namekian_dirt.
 */
public class NamekianDirtPathBlock extends Block {

    private static final VoxelShape SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 15.0, 16.0);

    public NamekianDirtPathBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected @NotNull VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level,
                                           @NotNull BlockPos pos, @NotNull CollisionContext ctx) {
        return SHAPE;
    }

    /** Igual que vainilla: no sobrevive con un bloque sólido encima, salvo vallas-puerta. */
    @Override
    protected boolean canSurvive(BlockState state, @NotNull LevelReader level, @NotNull BlockPos pos) {
        BlockState above = level.getBlockState(pos.above());
        return !above.isSolid() || above.getBlock() instanceof FenceGateBlock;
    }

    @Override
    protected @NotNull BlockState updateShape(BlockState state, @NotNull Direction direction,
                                              @NotNull BlockState neighborState, @NotNull LevelAccessor level,
                                              @NotNull BlockPos pos, @NotNull BlockPos neighborPos) {
        if (direction == Direction.UP && !state.canSurvive(level, pos)) {
            return ModBlocks.NAMEKIAN_DIRT.get().defaultBlockState();
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    /** Colocarlo donde no cabe lo convierte ya en tierra, sin pasar por el camino. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return !this.defaultBlockState().canSurvive(context.getLevel(), context.getClickedPos())
                ? ModBlocks.NAMEKIAN_DIRT.get().defaultBlockState()
                : super.getStateForPlacement(context);
    }

    @Override
    protected boolean useShapeForLightOcclusion(@NotNull BlockState state) {
        return true;
    }

    @Override
    protected boolean isPathfindable(@NotNull BlockState state, @NotNull net.minecraft.world.level.pathfinder.PathComputationType type) {
        return false;
    }
}