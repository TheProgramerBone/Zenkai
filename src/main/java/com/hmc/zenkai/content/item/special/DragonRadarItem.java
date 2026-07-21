package com.hmc.zenkai.content.item.special;

import com.hmc.zenkai.content.sound.ModSounds;
import com.hmc.zenkai.util.ModTags;
import com.hmc.zenkai.worldgen.LootedDragonBalls;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
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
import net.minecraft.world.level.levelgen.structure.Structure;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class DragonRadarItem extends Item {
    private static final String RADAR_TAG = "RadarStartTime";
    private static final int ACTIVE_DURATION_TICKS = 20 * 20; // 20 seconds
    private static final int NEAR_RADIUS_SQR = 16 * 16;
    private static final int DETECTION_RADIUS = 32;      // escaneo de bloques cercano
    private static final int STRUCTURE_SEARCH_CHUNKS = 100;
    private static final int LOOTED_MATCH_RADIUS = 32;   // margen entre la esfera y el inicio de su estructura
    private static final int TARGET_CACHE_TICKS = 200;   // 10 s entre búsquedas completas
    /** Objetivo cacheado por jugador: {posLong, tickDeCaducidad}. Solo servidor, no persiste. */
    private static final java.util.Map<UUID, long[]> TARGET_CACHE = new java.util.HashMap<>();

    public DragonRadarItem(Properties properties) {
        super(properties);
    }



    @Override
    public void appendHoverText(@NotNull ItemStack stack, @NotNull TooltipContext context, List<Component> tooltipComponents, @NotNull TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("tooltip.zenkai.dragon_ball_radar"));
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
    public void inventoryTick(@NotNull ItemStack stack, Level level, net.minecraft.world.entity.@NotNull Entity entity, int slot, boolean selected) {
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
        BlockPos nearest = findNearestDragonBall(level, player, player.blockPosition());
        if (nearest != null) {
            updateRadarDirection(stack, player, slot, player.getX(), player.getZ(), nearest.getX(), nearest.getZ(), player.getYRot());
            double distanceSqr = player.blockPosition().distToCenterSqr(nearest.getX(), nearest.getY(), nearest.getZ());
            if (distanceSqr <= NEAR_RADIUS_SQR) {
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable("messages.zenkai.dragon_ball_radar_near"), true);
                player.playNotifySound(ModSounds.DRAGON_BALL_RADAR_NEAR.get(), SoundSource.PLAYERS, 0.85F, 1.0F);
            } else {
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable("messages.zenkai.dragon_ball_radar_searching"), true);
                player.playNotifySound(ModSounds.DRAGON_BALL_RADAR_SEARCHING.get(), SoundSource.PLAYERS, 0.85F, 1.0F);
            }
        } else {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable("messages.zenkai.dragon_ball_radar_not_in_range"), true);
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

    /**
     * 1) Esferas físicas cerca (incluye las que un jugador haya colocado en su base).
     * 2) Si no hay, la estructura NO saqueada más cercana, cacheada por jugador.
     */
    private BlockPos findNearestDragonBall(Level level, Player player, BlockPos origin) {
        BlockPos nearby = scanNearbyBalls(level, origin);
        if (nearby != null) return nearby;

        if (!(level instanceof ServerLevel serverLevel)) return null;

        long[] cached = TARGET_CACHE.get(player.getUUID());
        if (cached != null && player.tickCount < cached[1]) return BlockPos.of(cached[0]);

        BlockPos target = locateUnlootedStructure(serverLevel, origin);
        if (target != null) {
            TARGET_CACHE.put(player.getUUID(),
                    new long[]{ target.asLong(), player.tickCount + TARGET_CACHE_TICKS });
        } else {
            TARGET_CACHE.remove(player.getUUID());
        }
        return target;
    }

    /** Escaneo de bloques en un cubo pequeño alrededor del jugador. */
    private static BlockPos scanNearbyBalls(Level level, BlockPos origin) {
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        BlockPos nearest = null;
        double closestDistanceSqr = Double.MAX_VALUE;

        int startY = Math.max(level.getMinBuildHeight(), origin.getY() - DETECTION_RADIUS);
        int endY   = Math.min(level.getMaxBuildHeight() - 1, origin.getY() + DETECTION_RADIUS);

        for (int x = origin.getX() - DETECTION_RADIUS; x <= origin.getX() + DETECTION_RADIUS; x++) {
            for (int z = origin.getZ() - DETECTION_RADIUS; z <= origin.getZ() + DETECTION_RADIUS; z++) {
                for (int y = startY; y <= endY; y++) {
                    mutable.set(x, y, z);
                    if (level.getBlockState(mutable).is(ModTags.Blocks.DRAGON_BALLS_BLOCK)) {
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

    /**
     * Busca la estructura más cercana de CADA esfera del tag por separado y se queda con la más
     * próxima que no esté marcada como saqueada. Ir por estructura (y no con el tag entero) es lo
     * que permite tener alternativa cuando la más cercana ya está vacía. Al iterar el tag, las
     * estructuras de Namek entran solas en cuanto las añadas a zenkai:dragon_balls.
     */
    private static BlockPos locateUnlootedStructure(ServerLevel level, BlockPos origin) {
        Optional<HolderSet.Named<Structure>> set =
                level.registryAccess().lookupOrThrow(Registries.STRUCTURE)
                        .get(ModTags.Structures.DRAGON_BALLS);
        if (set.isEmpty()) return null;

        LootedDragonBalls looted = LootedDragonBalls.get(level);
        BlockPos best = null;
        double bestSqr = Double.MAX_VALUE;

        for (Holder<Structure> holder : set.get()) {
            var found = level.getChunkSource().getGenerator().findNearestMapStructure(
                    level, HolderSet.direct(holder), origin, STRUCTURE_SEARCH_CHUNKS, false);
            if (found == null) continue;
            BlockPos pos = found.getFirst();
            if (looted.isLootedNear(pos, LOOTED_MATCH_RADIUS)) continue;
            double d = origin.distSqr(pos);
            if (d < bestSqr) { bestSqr = d; best = pos; }
        }
        return best;
    }
}