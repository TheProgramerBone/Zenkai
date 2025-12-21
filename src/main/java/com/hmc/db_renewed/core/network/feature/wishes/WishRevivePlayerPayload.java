package com.hmc.db_renewed.core.network.feature.wishes;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record WishRevivePlayerPayload(String targetName) implements CustomPacketPayload {
    public static final Type<WishRevivePlayerPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("db_renewed","wish_revive_player"));

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
                String targetName = payload.targetName() == null ? "" : payload.targetName().trim();
                if (targetName.isEmpty()) {
                    invoker.displayClientMessage(Component.translatable("messages.db_renewed.player_revive_failed"), false);
                    return;
                }

                ServerPlayer target = invoker.server.getPlayerList().getPlayerByName(targetName);
                if (target == null) {
                    invoker.displayClientMessage(Component.translatable("messages.db_renewed.player_revive_failed"), false);
                    return;
                }

                // TODO: integra tu capability/flag isDead (si existe)
                // Example:
                // target.getCapability(DeathCapProvider.CAP).ifPresent(cap -> cap.setDead(false));

                // Restaurar estado básico
                target.setHealth(target.getMaxHealth());

                // Determinar dimensión y posición de respawn
                ServerLevel dest = target.server.getLevel(target.getRespawnDimension());
                if (dest == null) dest = target.serverLevel(); // fallback

                BlockPos respawnPos = target.getRespawnPosition();
                if (respawnPos == null) respawnPos = dest.getSharedSpawnPos();

                target.teleportTo(dest,
                        respawnPos.getX() + 0.5,
                        respawnPos.getY(),
                        respawnPos.getZ() + 0.5,
                        target.getYRot(),
                        target.getXRot());

                // Feedback para quien pidió el deseo
                invoker.displayClientMessage(Component.translatable("messages.db_renewed.player_revived"), false);

                // Final común (cerrar GUI, quitar Shenlong, etc.)
                WishFinalizer.finalizeWish(invoker);
            });
        }
    }
}
