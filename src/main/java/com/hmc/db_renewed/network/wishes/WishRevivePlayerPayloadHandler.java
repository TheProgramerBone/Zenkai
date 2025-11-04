package com.hmc.db_renewed.network.wishes;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffects;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;

public final class WishRevivePlayerPayloadHandler {
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
