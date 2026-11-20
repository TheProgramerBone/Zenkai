package com.hmc.zenkai.content.block;

import com.hmc.zenkai.content.blockentity.GravityChamberPortBlockEntity;
import com.hmc.zenkai.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Puerto de energía de la cámara de gravedad (estilo válvula de Mekanism). Bloque macizo que forma
 * parte de la PARED: por fuera se le enchufan cables y la FE pasa a la consola con la que está
 * enlazado. El enlace lo hace la consola al validar la sala (el puerto toca su interior). Sin consola
 * no ofrece capability. Clic derecho: dice a qué consola está enlazado.
 */
public class GravityChamberPortBlock extends BaseEntityBlock {

    public static final MapCodec<GravityChamberPortBlock> CODEC = simpleCodec(GravityChamberPortBlock::new);

    public GravityChamberPortBlock(Properties properties) { super(properties); }

    @Override
    protected @NotNull MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    public @NotNull RenderShape getRenderShape(@NotNull BlockState state) { return RenderShape.MODEL; }

    @Override
    public @Nullable BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new GravityChamberPortBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(
            @NotNull Level level, @NotNull BlockState state, @NotNull BlockEntityType<T> type) {
        if (level.isClientSide) return null;
        return createTickerHelper(type, ModBlockEntities.GRAVITY_CHAMBER_PORT.get(), GravityChamberPortBlockEntity::serverTick);
    }

    @Override
    protected @NotNull InteractionResult useWithoutItem(@NotNull BlockState state, @NotNull Level level,
                                                        @NotNull BlockPos pos, @NotNull Player player,
                                                        @NotNull BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (level.getBlockEntity(pos) instanceof GravityChamberPortBlockEntity port) {
            BlockPos c = port.controller();
            player.displayClientMessage(c == null
                    ? Component.translatable("messages.zenkai.gravity_chamber.port_unlinked")
                    : Component.translatable("messages.zenkai.gravity_chamber.port_linked", c.getX(), c.getY(), c.getZ()), true);
        }
        return InteractionResult.CONSUME;
    }
}
