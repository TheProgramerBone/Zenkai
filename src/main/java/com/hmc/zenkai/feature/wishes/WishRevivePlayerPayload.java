package com.hmc.zenkai.feature.wishes;

import com.hmc.zenkai.config.ServerConfig;
import com.hmc.zenkai.feature.advancement.ZenkaiTriggers;
import com.hmc.zenkai.feature.player.OtherworldManager;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
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
                if (!ServerConfig.isEnabled(ServerConfig.WishType.REVIVE_PLAYER)) {
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

                // "Revivir y traer aquí". Si el invocador está él mismo en el Otro Mundo (hay
                // esferas allí), traerlo "aquí" lo dejaría donde ya estaba: en ese caso vale
                // la resurrección normal, a su cama.
                if (invoker.level() instanceof ServerLevel level
                        && !OtherworldManager.isInOtherworld(invoker)) {
                    Vec3 spot = WishSpawnPoint.besidePlayer(level, invoker);
                    OtherworldManager.reviveAt(target, level, spot.x, spot.y, spot.z);
                    spawnParticles(level, spot);
                } else {
                    OtherworldManager.revive(target);
                }

                target.displayClientMessage(Component.translatable("messages.zenkai.player_revived"), false);
                invoker.displayClientMessage(Component.translatable("messages.zenkai.player_revived"), false);
                ZenkaiTriggers.WISH_GRANTED.get().trigger(invoker, "revive_player");
            });
        }
    }

    /** Columna de partículas en el punto de llegada. Sin esto, un jugador apareciendo de
     *  la nada a dos bloques es indistinguible de alguien que acaba de entrar al servidor. */
    private static void spawnParticles(ServerLevel level, Vec3 at) {
        for (int i = 0; i <= 6; i++) {
            level.sendParticles(ParticleTypes.ENCHANT,
                    at.x, at.y + i * 0.5, at.z, 12, 0.3, 0.2, 0.3, 0.05);
        }
    }
}