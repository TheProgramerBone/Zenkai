package com.hmc.zenkai.core.network.feature.wishes;

import com.hmc.zenkai.content.entity.shenlong.ShenLongEntity;
import com.hmc.zenkai.content.sound.ModSounds;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class WishFinalizer {
    private WishFinalizer() {}

    public static void finalizeWish(ServerPlayer player) {
        player.closeContainer();
        player.playNotifySound(ModSounds.WISH_GRANTED.get(), SoundSource.PLAYERS, 0.8F, 1.0F);

        // Buscar el Shenlong más cercano y consumir UN deseo del pool.
        List<ShenLongEntity> nearby = player.level().getEntitiesOfClass(
                ShenLongEntity.class,
                player.getBoundingBox().inflate(32)
        );
        Optional<ShenLongEntity> opt = nearby.stream()
                .min(Comparator.comparingDouble(e -> e.distanceToSqr(player)));

        if (opt.isEmpty()) {
            // Sin dragón (no debería pasar): solo el mensaje de deseo concedido.
            player.displayClientMessage(Component.translatable("messages.zenkai.wish_granted"), false);
            return;
        }

        ShenLongEntity shenlong = opt.get();
        boolean despawn = shenlong.consumeWish();
        int remaining = shenlong.getWishesRemaining();

        if (despawn || remaining <= 0) {
            // Era el último deseo: mensaje simple y el dragón se va.
            player.displayClientMessage(Component.translatable("messages.zenkai.wish_granted"), false);
            shenlong.discard();
        } else {
            // Aún quedan deseos: avisar cuántos.
            player.displayClientMessage(
                    Component.translatable("messages.zenkai.wish_granted_remaining", remaining), false);
        }
    }
}