package com.hmc.zenkai.core.network.feature.wishes;

import com.hmc.zenkai.core.config.WishConfig;
import com.hmc.zenkai.core.network.feature.player.OtherworldManager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record WishRevivePlayerPayload(String targetName) implements CustomPacketPayload {
    public static final Type<WishRevivePlayerPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("zenkai","wish_revive_player"));

    public static final StreamCodec<RegistryFriendlyByteBuf, WishRevivePlayerPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, WishRevivePlayerPayload::targetName,
                    WishRevivePlayerPayload::new
            );
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static final class WishRevivePlayerPayloadHandler {
        public static void handle(WishRevivePlayerPayload payload, IPayloadContext ctx) {
            ctx.enqueueWork(() -> {
                ServerPlayer invoker = (ServerPlayer) ctx.player();
                if (!WishConfig.isEnabled(WishConfig.WishType.REVIVE_PLAYER)) {
                    invoker.displayClientMessage(Component.translatable("messages.zenkai.wish_disabled"), false);
                    return;
                }

                String targetName = payload.targetName() == null ? "" : payload.targetName().trim();
                if (targetName.isEmpty()) {
                    invoker.displayClientMessage(Component.translatable("messages.zenkai.player_revive_failed"), false);
                    return;
                }

                ServerPlayer target = invoker.server.getPlayerList().getPlayerByName(targetName);
                // Solo jugadores online y que estén realmente en el otro mundo.
                if (target == null || !OtherworldManager.isInOtherworld(target)) {
                    invoker.displayClientMessage(Component.translatable("messages.zenkai.player_revive_failed"), false);
                    return;
                }
                WishFinalizer.finalizeWish(invoker, Component.translatable(
                        "messages.zenkai.wish_desc.revive_player", target.getDisplayName()));
                OtherworldManager.revive(target); // misma lógica que el comando
                target.displayClientMessage(Component.translatable("messages.zenkai.player_revived"), false);
                invoker.displayClientMessage(Component.translatable("messages.zenkai.player_revived"), false);
            });
        }
    }
}