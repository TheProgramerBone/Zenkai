package com.hmc.db_renewed.block.custom;

import com.hmc.db_renewed.block.ModBlocks;
import com.hmc.db_renewed.block.entity.AllDragonBallsEntity;
import com.hmc.db_renewed.block.entity.ModBlockEntities;
import com.hmc.db_renewed.block.entity.client.AllDragonBallsRenderer;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AllDragonBallsBlock extends BaseEntityBlock {
    public AllDragonBallsBlock(Properties properties) {
        super(properties);
    }

    public static final MapCodec<AllDragonBallsBlock> CODEC = simpleCodec(AllDragonBallsBlock::new);

    private static final VoxelShape SHAPE = Block.box(-8.0, 0.0, -8.0, 24.0, 8.0, 24.0);


    @Override
    protected @NotNull VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext context) {
        return SHAPE;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AllDragonBallsEntity(pos, state);
    }


    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide && level instanceof ServerLevel serverLevel) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof AllDragonBallsEntity dragonBallsEntity) {
                dragonBallsEntity.triggerSummonAnimation();
                player.displayClientMessage(Component.literal("Come forth Shenron and grant my wish!"), true);
            }

            long currentTime = serverLevel.getDayTime();
            long timeOfDay = currentTime % 24000L;
            long ticksUntilNight = timeOfDay < 13000 ? 13000 - timeOfDay : (24000 - timeOfDay + 13000);
            serverLevel.setDayTime(currentTime + ticksUntilNight);

            serverLevel.sendParticles(
                        ParticleTypes.ENCHANT,
                        pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5,
                        50,
                        0.5, 0.5, 0.5, // dispersión
                        0.1
            );

            summonShenron(level, pos);
            }
        return InteractionResult.SUCCESS;
    }

    private void summonShenron(Level level, BlockPos pos) {
        EntityType<?> entityType = EntityType.ENDERMAN;
        var entity = entityType.create(level);
        if (entity != null) {
            entity.moveTo(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 0, 0);
            level.addFreshEntity(entity);
        }
    }

    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        super.playerWillDestroy(level, pos, state, player);

        if (!level.isClientSide) {
            dropItem(level, pos, ModBlocks.DRAGON_BALL_1.get().asItem());
            dropItem(level, pos, ModBlocks.DRAGON_BALL_2.get().asItem());
            dropItem(level, pos, ModBlocks.DRAGON_BALL_3.get().asItem());
            dropItem(level, pos, ModBlocks.DRAGON_BALL_4.get().asItem());
            dropItem(level, pos, ModBlocks.DRAGON_BALL_5.get().asItem());
            dropItem(level, pos, ModBlocks.DRAGON_BALL_6.get().asItem());
            dropItem(level, pos, ModBlocks.DRAGON_BALL_7.get().asItem());
        }
        return state;
    }

    private void dropItem(Level level, BlockPos pos, Item item) {
        Block.popResource(level, pos, new ItemStack(item));
    }
}
