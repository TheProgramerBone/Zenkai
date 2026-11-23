package com.hmc.zenkai.content.block;

import com.hmc.zenkai.content.blockentity.GravityChamberBlockEntity;
import com.hmc.zenkai.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
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
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Consola de la cámara de gravedad (Fase 3 de la 0.7.0). Como la máquina de gravedad de la nave
 * de Capsule Corp: una columna baja en el CENTRO del suelo de una sala esférica cerrada. Toda la
 * lógica vive en {@link GravityChamberBlockEntity}; aquí solo blockstate, forma y clic.
 *
 * ACTIVE dice si genera gravedad (luz y textura de la pantalla). Sin GeckoLib: no tiene partes que
 * se muevan. Clic derecho abre la pantalla (no hay otra interacción que robe el clic).
 */
public class GravityChamberBlock extends BaseEntityBlock {

    public static final MapCodec<GravityChamberBlock> CODEC = simpleCodec(GravityChamberBlock::new);

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    /**
     * Pedestal + columna + cabezal en atril (models/block/gravity_chamber.json). El panel va inclinado
     * 22,5 grados hacia el frente; la caja del cabezal lo envuelve entero (no hay forma inclinada) y no
     * depende de FACING porque el panel ocupa casi la misma huella girado a cualquier lado.
     */
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(1, 0, 1, 15, 3, 15),
            Block.box(5, 3, 5, 11, 8, 11),
            Block.box(2, 8, 2, 14, 16, 14));

    public GravityChamberBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(ACTIVE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING, ACTIVE);
    }

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

    @Override
    protected @NotNull VoxelShape getShape(@NotNull BlockState s, @NotNull BlockGetter l, @NotNull BlockPos p,
                                           @NotNull CollisionContext c) {
        return SHAPE;
    }

    @Override
    protected @NotNull MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    public @NotNull RenderShape getRenderShape(@NotNull BlockState state) { return RenderShape.MODEL; }

    @Override
    public @Nullable BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new GravityChamberBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(
            @NotNull Level level, @NotNull BlockState state, @NotNull BlockEntityType<T> type) {
        // En cliente no tickea: el holograma lo dibuja GravityHologramRenderer con lo sincronizado.
        if (level.isClientSide) return null;
        return createTickerHelper(type, ModBlockEntities.GRAVITY_CHAMBER.get(), GravityChamberBlockEntity::serverTick);
    }

    @Override
    protected @NotNull InteractionResult useWithoutItem(@NotNull BlockState state, @NotNull Level level,
                                                        @NotNull BlockPos pos, @NotNull Player player,
                                                        @NotNull BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (level.getBlockEntity(pos) instanceof GravityChamberBlockEntity be && player instanceof ServerPlayer sp) {
            be.revalidateNow();
            sp.openMenu(be, buf -> {
                buf.writeBlockPos(pos);
                buf.writeBoolean(be.droneView());   // GravityChamberMenu la lee para reabrir en la misma vista
            });
        }
        return InteractionResult.CONSUME;
    }

    /** Suelta la mejora y quita su efecto a los mobs que tenía frenados. */
    @Override
    protected void onRemove(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos,
                            @NotNull BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof GravityChamberBlockEntity be) {
            be.onBroken();
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}
