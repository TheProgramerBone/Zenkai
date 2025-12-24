package com.hmc.db_renewed.core.network.feature.wishes;

import com.hmc.db_renewed.content.effect.ModEffects;
import com.hmc.db_renewed.core.network.feature.stats.DataAttachments;
import com.hmc.db_renewed.core.network.feature.player.PlayerStatsAttachment;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record WishImmortalPayload() implements CustomPacketPayload {
    public static final Type<WishImmortalPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("db_renewed","wish_immortal"));
    public static final StreamCodec<RegistryFriendlyByteBuf, WishImmortalPayload> STREAM_CODEC =
            StreamCodec.unit(new WishImmortalPayload());
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static final class WishImmortalPayloadHandler {
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
}