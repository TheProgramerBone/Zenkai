package com.hmc.zenkai.core.network.feature.wishes;

import com.hmc.zenkai.content.entity.shenlong.ShenLongEntity;
import com.hmc.zenkai.content.sound.ModSounds;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;

import java.util.Comparator;
import java.util.List;

public final class WishFinalizer {
    private WishFinalizer() {}

    public static void finalizeWish(ServerPlayer player) {
        player.closeContainer();
        player.playNotifySound(ModSounds.WISH_GRANTED.get(), SoundSource.PLAYERS, 0.8F, 1.0F);
        player.displayClientMessage(Component.translatable("messages.zenkai.wish_granted"), false);

        // (2) Pool de deseos: consumir UN deseo del Shenlong más cercano.
        // Solo se descarta el dragón cuando el pool llega a 0.
        List<ShenLongEntity> nearby = player.level().getEntitiesOfClass(
                ShenLongEntity.class,
                player.getBoundingBox().inflate(32)
        );

        nearby.stream()
                .min(Comparator.comparingDouble(e -> e.distanceToSqr(player)))
                .ifPresent(shenlong -> {
                    if (shenlong.consumeWish()) {
                        shenlong.discard();
                    }
                });
    }
}