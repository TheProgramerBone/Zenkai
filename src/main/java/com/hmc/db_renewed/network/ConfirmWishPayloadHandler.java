package com.hmc.db_renewed.network;

import com.hmc.db_renewed.config.WishConfig;
import com.hmc.db_renewed.entity.ModEntities;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class ConfirmWishPayloadHandler {
    public static void handle(ConfirmWishPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;

            ItemStack chosen = payload.chosen();
            if (chosen == null || chosen.isEmpty()) return;

            // Validar con config
            ItemStack result = WishConfig.resolveWishStack(chosen);
            if (result.isEmpty()) {
                player.sendSystemMessage(Component.literal("Ese ítem no se puede desear."));
                return;
            }

            // Dar el stack al jugador
            boolean added = player.getInventory().add(result);
            if (!added) {
                player.drop(result, false);
            }

            // Shenlong desaparece
            player.level().getEntitiesOfClass(
                    ModEntities.SHENLONG.get().getBaseClass(),
                    player.getBoundingBox().inflate(32)
            ).forEach(Entity::discard);

            // Cerrar GUI
            player.closeContainer();
        });
    }
}