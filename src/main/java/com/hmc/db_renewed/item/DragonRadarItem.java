package com.hmc.db_renewed.item;

import com.hmc.db_renewed.sound.ModSounds;
import com.hmc.db_renewed.util.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class DragonRadarItem extends Item {
    private static final String RADAR_TAG = "RadarStartTime";
    private static final int ACTIVE_DURATION_TICKS = 20 * 20; // 20 seconds
    private static final int DETECTION_RADIUS = 128;
    private static final int NEAR_RADIUS_SQR = 16 * 16;

    public DragonRadarItem(Properties properties) {
        super(properties);
    }



    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("tooltip.db_renewed.dragon_ball_radar"));
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, Player player, @NotNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!level.isClientSide) {
            CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
            CompoundTag tag = data.copyTag();

            tag.putInt(RADAR_TAG, player.tickCount);
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
            player.getCooldowns().addCooldown(this, ACTIVE_DURATION_TICKS);
        }

        player.playNotifySound(ModSounds.DRAGON_BALL_RADAR_USE.get(), SoundSource.PLAYERS, 0.9F, 0.85F);
        return InteractionResultHolder.success(stack);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, net.minecraft.world.entity.Entity entity, int slot, boolean selected) {
        if (level.isClientSide || !(entity instanceof Player player)) return;

        CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = data.copyTag();

        if (!tag.contains(RADAR_TAG)) {
            resetRadarVisual(stack, player, slot);
            return;
        }

        int startTick = tag.getInt(RADAR_TAG);
        int elapsed = player.tickCount - startTick;

        if (elapsed >= ACTIVE_DURATION_TICKS) {
            tag.remove(RADAR_TAG);
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
            resetRadarVisual(stack, player, slot);
            return;
        }

        if (player.tickCount % 20 != 0) return;
        BlockPos nearest = findNearestDragonBall(level, player.blockPosition(), DETECTION_RADIUS);
        if (nearest != null) {
            updateRadarDirection(stack, player, slot, player.getX(), player.getZ(), nearest.getX(), nearest.getZ(), player.getYRot());
            double distanceSqr = player.blockPosition().distToCenterSqr(nearest.getX(), nearest.getY(), nearest.getZ());
            if (distanceSqr <= NEAR_RADIUS_SQR) {
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable("messages.db_renewed.dragon_ball_radar_near"), true);
                player.playNotifySound(ModSounds.DRAGON_BALL_RADAR_NEAR.get(), SoundSource.PLAYERS, 0.85F, 1.0F);
            } else {
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable("messages.db_renewed.dragon_ball_radar_searching"), true);
                player.playNotifySound(ModSounds.DRAGON_BALL_RADAR_SEARCHING.get(), SoundSource.PLAYERS, 0.85F, 1.0F);
            }
        } else {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable("messages.db.renewed.dragon_ball_radar_not_in_range"), true);
            resetRadarVisual(stack, player, slot);
        }
    }

    private void updateRadarDirection(ItemStack stack, Player player, int slot, double px, double pz, double tx, double tz, float playerYawDegrees) {
        double angleToTarget = Math.atan2(tz - pz, tx - px);
        double yawRadians = Math.toRadians(playerYawDegrees);
        double relativeAngle = angleToTarget - yawRadians;

        double normalized = (relativeAngle / (2 * Math.PI) + 1.0) % 1.0;
        int index = (int) Math.floor(normalized * 32.0) % 32;

        stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(index + 1));
        if (player.getInventory().getItem(slot) == stack) {
            player.getInventory().setItem(slot, stack.copy());
        }
    }

    private void resetRadarVisual(ItemStack stack, Player player, int slot) {
        stack.remove(DataComponents.CUSTOM_MODEL_DATA);

        if (player.getInventory().getItem(slot) == stack) {
            player.getInventory().setItem(slot, stack.copy());
        }
    }

    private BlockPos findNearestDragonBall(Level level, BlockPos origin, int radius) {
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        BlockPos nearest = null;
        double closestDistanceSqr = Double.MAX_VALUE;

        int startX = origin.getX() - radius;
        int endX = origin.getX() + radius;
        int startY = Math.max(0, origin.getY() - radius);
        int endY = Math.min(level.getMaxBuildHeight(), origin.getY() + radius);
        int startZ = origin.getZ() - radius;
        int endZ = origin.getZ() + radius;

        for (int x = startX; x <= endX; x++) {
            for (int y = startY; y <= endY; y++) {
                for (int z = startZ; z <= endZ; z++) {
                    mutable.set(x, y, z);
                    BlockState state = level.getBlockState(mutable);
                    if (state.is(ModTags.Blocks.DRAGON_BALLS)) {
                        double distSqr = origin.distSqr(mutable);
                        if (distSqr < closestDistanceSqr) {
                            closestDistanceSqr = distSqr;
                            nearest = mutable.immutable();
                        }
                    }
                }
            }
        }
        return nearest;
    }
}