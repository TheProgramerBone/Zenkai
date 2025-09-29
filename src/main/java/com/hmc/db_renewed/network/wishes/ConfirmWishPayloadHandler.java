package com.hmc.db_renewed.network.wishes;

import com.hmc.db_renewed.config.WishConfig;
import com.hmc.db_renewed.entity.ModEntities;
import com.hmc.db_renewed.gui.wishes.StackWishMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class ConfirmWishPayloadHandler {
    public static void handle(final ConfirmWishPayload payload, final IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) ctx.player();

            if (!(player.containerMenu instanceof StackWishMenu menu)) {
                player.displayClientMessage(Component.translatable("messages.db_renewed.no_open_wish"), false);
                return;
            }

            ItemStack chosen = menu.getChosenItem();
            if (chosen == null || chosen.isEmpty()) {
                player.displayClientMessage(Component.translatable("messages.db_renewed.no_chosen_item"), false);
                return;
            }

            // Resolver con config (conserva NBT si resolveWishStack lo hace así)
            ItemStack resolved = WishConfig.resolveWishStack(chosen);

            if (resolved.isEmpty()) {
                player.displayClientMessage(Component.translatable("messages.db_renewed.invalid_wish"), false);
                return;
            }

            if (!player.getInventory().add(resolved.copy())) {
                player.drop(resolved.copy(), false);
            }

            // Cerrar GUI
            player.closeContainer();
            menu.clearChosenItem();

            // Matar únicamente a Shenlong cerca
            EntityType<?> shenlongType = ModEntities.SHENLONG.get();
            player.level().getEntitiesOfClass(
                    Entity.class,
                    player.getBoundingBox().inflate(32),
                    e -> e.getType() == shenlongType
            ).forEach(Entity::discard);
        });
    }
}