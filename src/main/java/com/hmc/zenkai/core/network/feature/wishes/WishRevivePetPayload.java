package com.hmc.zenkai.core.network.feature.wishes;

import com.hmc.zenkai.content.entity.overworld.ShenLongEntity;
import com.hmc.zenkai.core.network.feature.player.PlayerLifeCycle;
import com.hmc.zenkai.core.network.feature.player.PlayerStatsAttachment;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Comparator;

public record WishRevivePetPayload(int index) implements CustomPacketPayload {
    public static final Type<WishRevivePetPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("zenkai", "wish_revive_pet"));

    public static final StreamCodec<RegistryFriendlyByteBuf, WishRevivePetPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, WishRevivePetPayload::index,
                    WishRevivePetPayload::new
            );

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static final class Handler {
        private Handler() {}

        public static void handle(WishRevivePetPayload payload, IPayloadContext ctx) {
            ctx.enqueueWork(() -> {
                if (!(ctx.player() instanceof ServerPlayer player)) return;
                if (!(player.level() instanceof ServerLevel level)) return;

                PlayerStatsAttachment stats = PlayerStatsAttachment.get(player);
                CompoundTag petNbt = stats.removeDeadPet(payload.index());
                if (petNbt == null) {
                    player.displayClientMessage(Component.translatable("messages.zenkai.pet_revive_failed"), false);
                    return;
                }

                Entity entity = EntityType.loadEntityRecursive(petNbt, level, e -> e);
                if (entity == null) {
                    stats.addDeadPet(petNbt); // no perder la mascota si algo falla
                    PlayerLifeCycle.sync(player);
                    player.displayClientMessage(Component.translatable("messages.zenkai.pet_revive_failed"), false);
                    return;
                }

                // Posición cinematográfica sobre las esferas (Shenlong) o junto al jugador.
                ShenLongEntity dragon = level.getEntitiesOfClass(
                                ShenLongEntity.class, player.getBoundingBox().inflate(48))
                        .stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(player))).orElse(null);
                double bx, by, bz;
                if (dragon != null) { bx = dragon.getX(); by = dragon.getY(); bz = dragon.getZ(); }
                else { bx = player.getX() + 1.0; by = player.getY(); bz = player.getZ() + 1.0; }

                entity.moveTo(bx, by + 3.0, bz, player.getYRot(), 0);
                entity.fallDistance = 0.0F;
                if (entity instanceof LivingEntity le) {
                    le.setHealth(le.getMaxHealth());
                    le.hurtTime = 0;
                    le.hurtDuration = 0;
                    le.deathTime = 0;
                    le.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 200, 0, false, false, false));
                }

                level.addFreshEntity(entity);

                for (int i = 0; i <= 6; i++) {
                    level.sendParticles(ParticleTypes.ENCHANT, bx, by + i * 0.5, bz, 12, 0.3, 0.2, 0.3, 0.05);
                }

                PlayerLifeCycle.sync(player); // actualizar la lista en el cliente
                player.displayClientMessage(Component.translatable("messages.zenkai.pet_revived"), false);
                WishFinalizer.finalizeWish(player, Component.translatable(
                        "messages.zenkai.wish_desc.revive_pet", entity.getDisplayName()));
            });
        }
    }
}