package com.hmc.zenkai.content.block;

import com.hmc.zenkai.content.blockentity.KiMachineBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * Bloque común de las máquinas de Ki sobre {@link KiMachineBlockEntity} (2026-11-23: la Fabricadora, la Estación de
 * imbuido y la Infusora eran tres copias idénticas). Estático y orientable como el generador; LIT enciende la cara
 * frontal mientras trabaja (lo pone el block entity con setLit). Clic derecho abre la pantalla; romperlo suelta el
 * contenido. Cada máquina solo da su block entity, su tipo y, si recuerda pestaña, {@link #writeOpenData}.
 *
 * @param <E> el block entity de la máquina
 */
public abstract class KiMachineBlock<E extends KiMachineBlockEntity> extends BaseEntityBlock {

    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    private final Class<E> beClass;
    private final Supplier<BlockEntityType<E>> type;

    protected KiMachineBlock(Properties props, Class<E> beClass, Supplier<BlockEntityType<E>> type) {
        super(props);
        this.beClass = beClass;
        this.type = type;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LIT, false));
    }

    /** El block entity nuevo de la máquina. */
    protected abstract E create(BlockPos pos, BlockState state);

    /** Lo que viaja al cliente al abrir, DESPUÉS de la posición (p. ej. la pestaña recordada). Por defecto, nada. */
    protected void writeOpenData(E be, RegistryFriendlyByteBuf buf) { }

    /** El ticker de servidor de la máquina. */
    protected abstract BlockEntityTicker<E> serverTicker();

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING, LIT); }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    protected @NotNull BlockState rotate(@NotNull BlockState state, @NotNull Rotation rot) {
        return state.setValue(FACING, rot.rotate(state.getValue(FACING)));
    }

    @Override
    protected @NotNull BlockState mirror(@NotNull BlockState state, @NotNull Mirror mirror) {
        Direction facing = state.getValue(FACING);
        return state.setValue(FACING, mirror.getRotation(facing).rotate(facing));
    }

    @Override public @NotNull RenderShape getRenderShape(@NotNull BlockState state) { return RenderShape.MODEL; }

    @Override
    public @Nullable BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return create(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(
            @NotNull Level level, @NotNull BlockState state, @NotNull BlockEntityType<T> t) {
        if (level.isClientSide) return null;
        return createTickerHelper(t, type.get(), serverTicker());
    }

    @Override
    protected @NotNull InteractionResult useWithoutItem(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos,
                                                        @NotNull Player player, @NotNull BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        BlockEntity be = level.getBlockEntity(pos);
        if (beClass.isInstance(be) && player instanceof ServerPlayer sp) {
            E machine = beClass.cast(be);
            sp.openMenu(machine, buf -> {
                buf.writeBlockPos(pos);
                writeOpenData(machine, buf);
            });
        }
        return InteractionResult.CONSUME;
    }

    @Override
    protected void onRemove(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos,
                            @NotNull BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof KiMachineBlockEntity be) {
            be.dropContents(level, pos);
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}
