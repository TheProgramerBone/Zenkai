package com.hmc.db_renewed.core.network.feature.wishes;

import com.hmc.db_renewed.content.entity.ModEntities;
import com.hmc.db_renewed.content.sound.ModSounds;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

public final class WishFinalizer {
    private WishFinalizer() {}

    public static void finalizeWish(ServerPlayer player) {
        player.closeContainer();
        player.playNotifySound(ModSounds.WISH_GRANTED.get(), SoundSource.PLAYERS, 0.8F, 1.0F);
        player.displayClientMessage(Component.translatable("messages.db_renewed.wish_granted"), false);
        EntityType<?> shenlongType = ModEntities.SHENLONG.get();
        player.level().getEntitiesOfClass(
                Entity.class,
                player.getBoundingBox().inflate(32),
                e -> e.getType() == shenlongType
        ).forEach(Entity::discard);
    }
}
