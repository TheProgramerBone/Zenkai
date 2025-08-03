package com.hmc.db_renewed.block.custom;

import com.hmc.db_renewed.block.ModBlocks;
import com.hmc.db_renewed.block.entity.AllDragonBalls.AllDragonBallsEntity;
import com.hmc.db_renewed.sound.ModSounds;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class AllDragonBallsBlock extends BaseEntityBlock {
    public AllDragonBallsBlock(Properties properties) {
        super(properties);
    }

    public static final MapCodec<AllDragonBallsBlock> CODEC = simpleCodec(AllDragonBallsBlock::new);

    private static final VoxelShape SHAPE = Block.box(-8.0, 0.0, -8.0, 24.0, 8.0, 24.0);

    private boolean ShenronSummoned = false;

    @Override
    protected @NotNull VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext context) {
        return SHAPE;
    }

    @Override
    public @NotNull RenderShape getRenderShape(@NotNull BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new AllDragonBallsEntity(pos, state);
    }


    @Override
    protected @NotNull MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected @NotNull InteractionResult useWithoutItem(@NotNull BlockState state, Level level, @NotNull BlockPos pos, Player player, @NotNull BlockHitResult hitResult) {
        player.playNotifySound((ModSounds.DRAGON_BALL_USE.get()), SoundSource.BLOCKS,1f,1f);
        if (!level.isClientSide && level instanceof ServerLevel serverLevel) {
            player.displayClientMessage(Component.translatable("messages.db.renewed.shenron_summon"), true);
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
            level.scheduleTick(pos, this, 20*4);
            summonShenron(level, pos);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos barrierPos = pos.offset(dx, 0, dz);
                    Interaction barrier = new Interaction(EntityType.INTERACTION,level);
                    barrier.setPos(barrierPos.getX() + 0.5, barrierPos.getY() - 0.2, barrierPos.getZ() + 0.5);
                    barrier.setInvulnerable(true);
                    barrier.setCustomNameVisible(false);
                    barrier.setNoGravity(true);
                    barrier.addTag("dragon_barrier");
                    serverLevel.addFreshEntity(barrier);
                }
            }
            ShenronSummoned = true;
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


    @Override
    public @NotNull BlockState playerWillDestroy(Level level, @NotNull BlockPos pos, @NotNull BlockState state, @NotNull Player player) {
        if (!level.isClientSide) {
            dropItem(level, pos, ModBlocks.DRAGON_BALL_1.get().asItem());
            dropItem(level, pos, ModBlocks.DRAGON_BALL_2.get().asItem());
            dropItem(level, pos, ModBlocks.DRAGON_BALL_3.get().asItem());
            dropItem(level, pos, ModBlocks.DRAGON_BALL_4.get().asItem());
            dropItem(level, pos, ModBlocks.DRAGON_BALL_5.get().asItem());
            dropItem(level, pos, ModBlocks.DRAGON_BALL_6.get().asItem());
            dropItem(level, pos, ModBlocks.DRAGON_BALL_7.get().asItem());
        }
        super.playerWillDestroy(level, pos, state, player);
        return state;
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, @NotNull BlockState state, @NotNull BlockEntityType<T> type) {
        return level.isClientSide ? null : (lvl, pos, st, be) -> {
            if (be instanceof AllDragonBallsEntity entity) {
                ServerLevel serverLevel = (ServerLevel) lvl;
                if (entity.isAnimating(serverLevel)) return;
                if (entity.hasFinishedAnimation(serverLevel)) {
                    entity.stopAnimation();
                    lvl.removeBlock(pos, false);
                    List<Interaction> barriers = serverLevel.getEntitiesOfClass(Interaction.class,
                            new AABB(pos).inflate(5),
                            i -> i.getTags().contains("dragon_barrier"));
                    for (Interaction barrier : barriers) {
                        barrier.discard();
                    }
                    return;
                }

                boolean shenronNearby = !serverLevel.getEntitiesOfClass(
                        net.minecraft.world.entity.monster.EnderMan.class,
                        new net.minecraft.world.phys.AABB(pos).inflate(10)).isEmpty();
                if (ShenronSummoned) {
                    if (!shenronNearby) {
                        ShenronSummoned = false;
                        entity.startAnimation(serverLevel);

                    }
                }
            }
        };
    }

    private void dropItem(Level level, BlockPos pos, Item item) {
        Block.popResource(level, pos, new ItemStack(item));
    }
}
