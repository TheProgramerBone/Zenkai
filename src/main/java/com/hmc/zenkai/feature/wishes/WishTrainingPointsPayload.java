package com.hmc.zenkai.feature.wishes;

import com.hmc.zenkai.config.ServerConfig;
import com.hmc.zenkai.feature.player.PlayerLifeCycle;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.registry.ZenkaiDataAttachments;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record WishTrainingPointsPayload() implements CustomPacketPayload {
    public static final Type<WishTrainingPointsPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("zenkai", "wish_training_points"));

    public static final StreamCodec<RegistryFriendlyByteBuf, WishTrainingPointsPayload> STREAM_CODEC =
            StreamCodec.unit(new WishTrainingPointsPayload());

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static final class WishTrainingPointsPayloadHandler {
        public static void handle(WishTrainingPointsPayload payload, IPayloadContext ctx) {
            ctx.enqueueWork(() -> {
                ServerPlayer player = (ServerPlayer) ctx.player();

                if (!ServerConfig.isEnabled(ServerConfig.WishType.TRAINING_POINTS)) {
                    player.displayClientMessage(Component.translatable("messages.zenkai.wish_disabled"), false);
                    return;
                }

                int amount = ServerConfig.trainingPointsAmount();
                PlayerStatsAttachment att = player.getData(ZenkaiDataAttachments.PLAYER_STATS.get());
                att.addTP(amount);
                PlayerLifeCycle.sync(player); // refleja el nuevo TP en el cliente

                player.displayClientMessage(
                        Component.translatable("messages.zenkai.training_points_granted", amount), false);

                WishFinalizer.finalizeWish(player, Component.translatable(
                        "messages.zenkai.wish_desc.training_points", amount));
            });
        }
    }
}