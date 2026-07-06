package com.hmc.zenkai.content.block;

import com.hmc.zenkai.worldgen.HtcTravel;
import com.hmc.zenkai.worldgen.ModDimensions;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;

/**
 * Bloque de portal a la Habitación del Tiempo. Click derecho:
 *  - si estás en la HTC → salir (vuelves a donde entraste; si no, a Kami).
 *  - si no → entrar a la HTC.
 * Va colocado tanto en la estructura de Kami como en la de la HTC.
 */
public class HtcPortalBlock extends Block {

    public HtcPortalBlock(Properties props) {
        super(props);
    }

    @Override
    protected @NotNull InteractionResult useWithoutItem(@NotNull BlockState state, @NotNull Level level,
                                                        @NotNull BlockPos pos, @NotNull Player player,
                                                        @NotNull BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            if (sp.level().dimension() == ModDimensions.HTC_LEVEL) {
                HtcTravel.exitHtc(sp);
            } else {
                HtcTravel.enterHtc(sp);
            }
        }
        return InteractionResult.SUCCESS;
    }
}