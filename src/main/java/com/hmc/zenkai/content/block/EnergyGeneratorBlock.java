package com.hmc.zenkai.content.block;

import com.hmc.zenkai.content.blockentity.EnergyGeneratorBlockEntity;
import com.hmc.zenkai.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Generador de FE. Bloque estático con blockstate: LIT dice si está ardiendo, y con eso basta
 * para cambiar la textura y emitir luz. Sin GeckoLib — no tiene partes que se muevan y un
 * modelo animado costaría el mismo mantenimiento que el banco (ver la deuda del geo del ítem)
 * a cambio de nada.
 *
 * INTERACCIÓN SIN GUI:
 *   clic derecho con combustible  -> lo mete
 *   clic derecho con la mano      -> saca lo que quede sin quemar
 * Es el contrato de un dispensador o una hoguera, y evita registrar un MenuType, una Screen y
 * un ContainerData para gestionar un único hueco.
 */
public class EnergyGeneratorBlock extends BaseEntityBlock {

    public static final MapCodec<EnergyGeneratorBlock> CODEC = simpleCodec(EnergyGeneratorBlock::new);

    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public EnergyGeneratorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(LIT, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING, LIT);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext ctx) {
        // getOpposite: se orienta mirando AL jugador, como el horno. Sin el opposite la cara
        // frontal queda pegada a la pared cada vez que lo colocas de frente.
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    /** Rotate y mirror no son opcionales aunque nadie use /rotate: las estructuras del mod
     *  se colocan con rotación aleatoria, y un bloque que las ignora sale mirando al norte
     *  en todas las variantes de la misma sala. */
    @Override
    protected @NotNull BlockState rotate(@NotNull BlockState state, @NotNull Rotation rot) {
        return state.setValue(FACING, rot.rotate(state.getValue(FACING)));
    }

    @Override
    protected @NotNull BlockState mirror(@NotNull BlockState state, @NotNull Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected @NotNull MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    public @NotNull RenderShape getRenderShape(@NotNull BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new EnergyGeneratorBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(
            @NotNull Level level, @NotNull BlockState state, @NotNull BlockEntityType<T> type) {
        if (level.isClientSide) return null;   // no hay nada que hacer en cliente
        return createTickerHelper(type, ModBlockEntities.ENERGY_GENERATOR.get(),
                EnergyGeneratorBlockEntity::serverTick);
    }

    @Override
    protected @NotNull InteractionResult useWithoutItem(@NotNull BlockState state,
                                                        @NotNull Level level,
                                                        @NotNull BlockPos pos,
                                                        @NotNull Player player,
                                                        @NotNull BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (level.getBlockEntity(pos) instanceof EnergyGeneratorBlockEntity be
                && player instanceof ServerPlayer sp) {
            // El BlockPos viaja al cliente porque el constructor de red del menú lo necesita
            // para el ContainerLevelAccess.
            sp.openMenu(be, buf -> buf.writeBlockPos(pos));
        }
        return InteractionResult.CONSUME;
    }

    /** Suelta la cola al romperlo. Sin esto se evapora, y son seis huecos de combustible. */
    @Override
    protected void onRemove(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos,
                            @NotNull BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())
                && level.getBlockEntity(pos) instanceof EnergyGeneratorBlockEntity be) {
            be.dropContents(level, pos);
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}