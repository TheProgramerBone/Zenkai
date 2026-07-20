package com.hmc.zenkai.core.network.feature.wishes;

import com.hmc.zenkai.content.entity.overworld.ShenLongEntity;
import com.hmc.zenkai.content.sound.ModSounds;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class WishFinalizer {
    private WishFinalizer() {}

    /** Compatibilidad: deseo sin descripción concreta. */
    public static void finalizeWish(ServerPlayer player) {
        finalizeWish(player, Component.translatable("messages.zenkai.wish_desc.unknown"));
    }

    /** wishDesc = qué pidió el jugador; se difunde a los jugadores cercanos. */
    public static void finalizeWish(ServerPlayer player, Component wishDesc) {
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
            WishBroadcast.nearby(player, Component.translatable(
                    "messages.zenkai.wish_granted_public",
                    player.getDisplayName(), wishDesc), false);
            return;
        }

        ShenLongEntity shenlong = opt.get();
        boolean despawn = shenlong.consumeWish();
        int remaining = shenlong.getWishesRemaining();

        if (despawn || remaining <= 0) {
            // Era el último deseo: mensaje simple y el dragón se va.
            WishBroadcast.nearby(player, Component.translatable(
                    "messages.zenkai.wish_granted_public",
                    player.getDisplayName(), wishDesc), false);
            shenlong.discard();
        } else {
            // Aún quedan deseos: avisar cuántos.
            WishBroadcast.nearby(player, Component.translatable(
                    "messages.zenkai.wish_granted_remaining_public",
                    player.getDisplayName(), wishDesc, remaining), false);
        }
    }
}