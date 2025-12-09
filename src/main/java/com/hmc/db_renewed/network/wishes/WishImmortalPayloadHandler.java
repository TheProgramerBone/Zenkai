package com.hmc.db_renewed.network.wishes;

import com.hmc.db_renewed.effect.ModEffects;
import com.hmc.db_renewed.network.stats.DataAttachments;
import com.hmc.db_renewed.network.stats.PlayerStatsAttachment;
import net.minecraft.world.effect.MobEffectInstance;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;

public final class WishImmortalPayloadHandler {
    public static void handle(WishImmortalPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) ctx.player();
            PlayerStatsAttachment att = player.getData(DataAttachments.PLAYER_STATS.get());
            player.addEffect(new MobEffectInstance(
                    ModEffects.IMMORTALITY,
                    MobEffectInstance.INFINITE_DURATION, 0, true, false, false
            ));
            player.displayClientMessage(Component.translatable("messages.db_renewed.immortal"), false);
            att.setImmortal(true);

            WishFinalizer.finalizeWish(player);
        });
    }
}