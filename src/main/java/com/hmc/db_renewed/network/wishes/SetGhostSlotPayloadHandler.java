package com.hmc.db_renewed.network.wishes;

import com.hmc.db_renewed.config.WishConfig;
import com.hmc.db_renewed.gui.wishes.StackWishMenu;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class SetGhostSlotPayloadHandler {
    public static void handle(final SetGhostSlotPayload payload, final IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) ctx.player();
            if (!(player.containerMenu instanceof StackWishMenu menu)) return;

            ItemStack chosen = payload.chosen();
            if (chosen == null || chosen.isEmpty()) {
                menu.clearChosenItem();
                return;
            }

            // Validación server-side: no permitir banned items
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(chosen.getItem());
            if (WishConfig.isBanned(id)) {
                player.displayClientMessage(
                        Component.translatable("messages.db_renewed.item_banned"), false
                );
                menu.clearChosenItem();
                return;
            }

            // Copiar item con NBT y count=1
            ItemStack copy = chosen.copy();
            copy.setCount(1);
            menu.setChosenItem(copy);
        });
    }
}