package com.hmc.zenkai.content.block;

import com.hmc.zenkai.content.blockentity.ScouterBenchBlockEntity;
import com.hmc.zenkai.registry.ModBlockEntities;
import com.hmc.zenkai.registry.ModSounds;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Banco de scouter: mejora y repara el aparato. Bloque GeckoLib con block entity.
 * WORKING vive en el BLOCKSTATE y no solo en el block entity: así el cliente se entera del
 * cambio por el camino normal de sincronización de bloques y el controlador de animación puede
 * leerlo sin inventarse un paquete. El progreso concreto sí es del BE — no cabe en un estado
 * y solo lo necesita quien tiene la GUI abierta.
 */
public class ScouterBenchBlock extends BaseEntityBlock {

    public static final MapCodec<ScouterBenchBlock> CODEC = simpleCodec(ScouterBenchBlock::new);

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty WORKING = BooleanProperty.create("working");

    // 16 y no 25: el modelo sobresale por encima del bloque a propósito, pero la colisión se
    // queda dentro de su celda. Con 25 no podías poner nada encima del banco y el jugador
    // chocaba con aire un bloque más arriba.
    private static final VoxelShape SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 16.0, 16.0);

    public ScouterBenchBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(WORKING, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING, WORKING);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    protected @NotNull VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level,
                                           @NotNull BlockPos pos, @NotNull CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    public @NotNull RenderShape getRenderShape(@NotNull BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    protected @NotNull MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    public @Nullable BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new ScouterBenchBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(
            @NotNull Level level, @NotNull BlockState state, @NotNull BlockEntityType<T> type) {
        // El trabajo es autoritativo y solo corre en servidor. En cliente hay un ticker
        // aparte que no toca lógica: solo enciende y apaga el bucle de sonido, y tiene que
        // correr aunque el jugador mire hacia otro lado.
        return createTickerHelper(type, ModBlockEntities.SCOUTER_BENCH.get(),
                level.isClientSide ? ScouterBenchBlockEntity::clientTick
                        : ScouterBenchBlockEntity::serverTick);
    }

    @Override
    protected @NotNull InteractionResult useWithoutItem(@NotNull BlockState state, @NotNull Level level,
                                                        @NotNull BlockPos pos, @NotNull Player player,
                                                        @NotNull BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof ScouterBenchBlockEntity be)) {
            return InteractionResult.PASS;
        }

        // Un solo jugador a la vez. Con dos GUIs abiertas, la segunda pelearía por el mismo
        // trabajo y por el mismo slot, y el "cancelar" de uno le comería el progreso al otro.
        if (!be.canBeUsedBy(player)) {
            player.displayClientMessage(
                    Component.translatable("messages.zenkai.scouter_bench.busy"), true);
            return InteractionResult.SUCCESS;
        }

        be.claim(player);
        level.playSound(null, pos, ModSounds.SCOUTER_BENCH_OPEN.get(),
                SoundSource.BLOCKS, 0.5f, 1.0f);
        player.openMenu(be, pos);
        return InteractionResult.CONSUME;
    }

    @Override
    protected void onRemove(BlockState state, @NotNull Level level, @NotNull BlockPos pos,
                            BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())
                && level.getBlockEntity(pos) instanceof ScouterBenchBlockEntity be) {
            // El scouter que hubiera dentro NO se traga con el bloque.
            Containers.dropContents(level, pos, be);
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}