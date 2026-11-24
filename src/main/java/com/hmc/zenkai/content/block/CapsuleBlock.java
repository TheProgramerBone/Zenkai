package com.hmc.zenkai.content.block;

import com.hmc.zenkai.content.blockentity.CapsuleBlockEntity;
import com.hmc.zenkai.feature.capsule.CapsuleTier;
import com.hmc.zenkai.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
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
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Cápsula Hoi-Poi colocada en el suelo (Fase 6). NO tiene ítem propio: la coloca {@link com.hmc.zenkai.content.item.CapsuleItem}
 * y el block entity guarda el stack entero (rango, contenido), que es lo que suelta al romperse. Se rompe de un golpe y no
 * tiene colisión, como una antorcha.
 *
 * Blockstate: {@code tier} (textura del metal), {@code full} (botón encendido) y {@code facing}. El clic lo resuelve
 * {@link CapsuleBlockEntity#onUse}: vacía → contorno, luego pantalla; armada → cancelar; llena → soltar.
 */
public class CapsuleBlock extends BaseEntityBlock {

    public static final MapCodec<CapsuleBlock> CODEC = simpleCodec(CapsuleBlock::new);

    public static final EnumProperty<CapsuleTier> TIER = EnumProperty.create("tier", CapsuleTier.class);
    public static final BooleanProperty FULL = BooleanProperty.create("full");
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    /**
     * Cápsula DE PIE (rehecha 2026-11-24 a petición del usuario): 4×4 px de planta y 12,5 de alto, como
     * models/block/capsule_*.json de tools/gen_capsules.py. El logo va en las cuatro caras, así que FACING ya no cambia la
     * forma; se conserva en el blockstate para no invalidar cápsulas ya colocadas ni estructuras guardadas.
     */
    private static final VoxelShape SHAPE = Block.box(6, 0, 6, 10, 12.5, 10);

    public CapsuleBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(TIER, CapsuleTier.COPPER).setValue(FULL, false)
                .setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(TIER, FULL, FACING);
    }

    @Override
    protected @NotNull MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    public @NotNull RenderShape getRenderShape(@NotNull BlockState state) { return RenderShape.MODEL; }

    @Override
    protected @NotNull VoxelShape getShape(@NotNull BlockState s, @NotNull BlockGetter l, @NotNull BlockPos p,
                                           @NotNull CollisionContext c) {
        return SHAPE;
    }

    @Override
    protected @NotNull VoxelShape getCollisionShape(@NotNull BlockState s, @NotNull BlockGetter l, @NotNull BlockPos p,
                                                    @NotNull CollisionContext c) {
        return Shapes.empty();
    }

    @Override
    protected boolean canSurvive(@NotNull BlockState state, @NotNull LevelReader level, @NotNull BlockPos pos) {
        return level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
    }

    /** Sin suelo debajo se rompe y suelta su stack (onRemove), como una antorcha. */
    @Override
    protected @NotNull BlockState updateShape(@NotNull BlockState state, @NotNull Direction dir, @NotNull BlockState other,
                                              @NotNull LevelAccessor level, @NotNull BlockPos pos, @NotNull BlockPos otherPos) {
        if (dir == Direction.DOWN && !state.canSurvive(level, pos)) {
            return net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, dir, other, level, pos, otherPos);
    }

    @Override
    protected @NotNull BlockState rotate(@NotNull BlockState state, @NotNull Rotation rot) {
        return state.setValue(FACING, rot.rotate(state.getValue(FACING)));
    }

    @Override
    protected @NotNull BlockState mirror(@NotNull BlockState state, @NotNull Mirror mirror) {
        Direction f = state.getValue(FACING);
        return state.setValue(FACING, mirror.getRotation(f).rotate(f));
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new CapsuleBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(
            @NotNull Level level, @NotNull BlockState state, @NotNull BlockEntityType<T> type) {
        if (level.isClientSide) return null;
        return createTickerHelper(type, ModBlockEntities.CAPSULE.get(), CapsuleBlockEntity::serverTick);
    }

    @Override
    protected @NotNull InteractionResult useWithoutItem(@NotNull BlockState state, @NotNull Level level,
                                                        @NotNull BlockPos pos, @NotNull Player player,
                                                        @NotNull BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (level.getBlockEntity(pos) instanceof CapsuleBlockEntity be && player instanceof ServerPlayer sp) {
            be.onUse(sp);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    protected void onRemove(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos,
                            @NotNull BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof CapsuleBlockEntity be) {
            ItemStack stack = be.takeCapsule();
            if (!stack.isEmpty()) Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack);
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    /** Pick-block en creativo: la cápsula tal cual (con su contenido no: una copia llena sería un duplicado). */
    @Override
    public @NotNull ItemStack getCloneItemStack(@NotNull BlockState state, @NotNull HitResult target, @NotNull LevelReader level,
                                                @NotNull BlockPos pos, @NotNull Player player) {
        return new ItemStack(com.hmc.zenkai.registry.ModItems.capsule(state.getValue(TIER)));
    }
}
