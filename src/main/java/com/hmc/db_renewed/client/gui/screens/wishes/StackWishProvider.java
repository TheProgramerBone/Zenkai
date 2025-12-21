package com.hmc.db_renewed.client.gui.screens.wishes;

import com.hmc.db_renewed.client.gui.StackWishMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;

public class StackWishProvider implements MenuProvider {
    @Override
    public Component getDisplayName() {
        return Component.translatable("screen.db_renewed.option.stack");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new StackWishMenu(id, inv);
    }
}