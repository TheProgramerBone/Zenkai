package com.hmc.db_renewed.network.wishes;

import com.hmc.db_renewed.effect.ModEffects;
import net.minecraft.world.effect.MobEffectInstance;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;

public final class WishImmortalPayloadHandler {
    public static void handle(WishImmortalPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) ctx.player();
            player.addEffect(new MobEffectInstance(
                    ModEffects.IMMORTALITY,
                    Integer.MAX_VALUE, 0, true, false, true
            ));
            player.displayClientMessage(Component.translatable("messages.db_renewed.immortal"), false);
            WishFinalizer.finalizeWish(player);
        });
    }
}