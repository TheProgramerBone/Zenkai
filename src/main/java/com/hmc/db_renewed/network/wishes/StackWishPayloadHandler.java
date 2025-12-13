package com.hmc.db_renewed.network.wishes;

import com.hmc.db_renewed.config.WishConfig;
import com.hmc.db_renewed.gui.wishes.StackWishMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class StackWishPayloadHandler {
    public static void handle(final StackWishPayload payload, final IPayloadContext ctx) {
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

            ItemStack resolved = WishConfig.resolveWishStack(chosen);

            if (resolved.isEmpty()) {
                player.displayClientMessage(Component.translatable("messages.db_renewed.invalid_wish"), false);
                return;
            }

            ItemHandlerHelper.giveItemToPlayer(player, resolved.copy());

            player.inventoryMenu.broadcastChanges();
            player.containerMenu.broadcastChanges();

            menu.clearChosenItem();
            WishFinalizer.finalizeWish(player);
        });
    }
}