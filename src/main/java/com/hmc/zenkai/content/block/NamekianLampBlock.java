package com.hmc.zenkai.content.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;

/** Lámpara namekiana: interruptor manual con click derecho. Se coloca encendida. */
public class NamekianLampBlock extends Block {

    public static final MapCodec<NamekianLampBlock> CODEC = simpleCodec(NamekianLampBlock::new);
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    public NamekianLampBlock(Properties props) {
        super(props);
        registerDefaultState(defaultBlockState().setValue(LIT, true));
    }

    @Override
    public @NotNull MapCodec<? extends NamekianLampBlock> codec() {
        return CODEC;
    }

    @Override
    protected @NotNull InteractionResult useWithoutItem(@NotNull BlockState state, @NotNull Level level,
                                                        @NotNull BlockPos pos, @NotNull Player player,
                                                        @NotNull BlockHitResult hit) {
        if (!level.isClientSide) {
            BlockState next = state.cycle(LIT);
            boolean lit = next.getValue(LIT);
            level.setBlock(pos, next, Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.5f, lit ? 0.9f : 0.6f);
            level.gameEvent(player, lit ? GameEvent.BLOCK_ACTIVATE : GameEvent.BLOCK_DEACTIVATE, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.@NotNull Builder<Block, BlockState> builder) {
        builder.add(LIT);
    }
}