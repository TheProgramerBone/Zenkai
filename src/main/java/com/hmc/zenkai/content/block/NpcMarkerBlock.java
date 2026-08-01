package com.hmc.zenkai.content.block;

import com.hmc.zenkai.content.blockentity.NpcMarkerBlockEntity;
import com.hmc.zenkai.network.OpenNpcMarkerPayload;
import com.hmc.zenkai.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Marcador invisible que spawnea y vigila un NPC de estructura. Viaja dentro del NBT. */
public class NpcMarkerBlock extends BaseEntityBlock {

    public static final MapCodec<NpcMarkerBlock> CODEC = simpleCodec(NpcMarkerBlock::new);

    public NpcMarkerBlock(BlockBehaviour.Properties props) {
        super(props);
    }

    public static BlockBehaviour.@NotNull Properties markerProperties() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.NONE)
                .noCollission()
                .noOcclusion()
                .noLootTable()
                .strength(-1.0F, 3600000.0F)   // irrompible como la bedrock
                .sound(SoundType.EMPTY)
                .pushReaction(PushReaction.BLOCK);
    }

    @Override
    protected @NotNull MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    protected @NotNull RenderShape getRenderShape(@NotNull BlockState state) { return RenderShape.INVISIBLE; }

    @Override
    public @Nullable BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new NpcMarkerBlockEntity(pos, state);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, @NotNull BlockState state, @NotNull BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        return createTickerHelper(type, ModBlockEntities.NPC_MARKER.get(),
                (lvl, pos, st, be) -> be.serverTick());
    }

    @Override
    protected @NotNull InteractionResult useWithoutItem(@NotNull BlockState state, Level level,
                                                        @NotNull BlockPos pos, @NotNull Player player,
                                                        @NotNull BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof NpcMarkerBlockEntity be)) return InteractionResult.PASS;
        if (!player.canUseGameMasterBlocks()) return InteractionResult.PASS;
        if (!(player instanceof ServerPlayer sp)) return InteractionResult.SUCCESS;

        PacketDistributor.sendToPlayer(sp, new OpenNpcMarkerPayload(
                pos,
                be.getNpcType() == null ? "" : be.getNpcType().toString(),
                be.getYaw(), be.getOffX(), be.getOffY(), be.getOffZ()));
        return InteractionResult.CONSUME;
    }
}