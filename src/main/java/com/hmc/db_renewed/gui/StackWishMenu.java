package com.hmc.db_renewed.gui;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

public class StackWishMenu extends AbstractContainerMenu {
    private final SimpleContainer ghostInv;

    public StackWishMenu(int id, Inventory playerInv) {
        super(ModMenuTypes.STACK_WISH.get(), id);
        this.ghostInv = new SimpleContainer(1);

        // Slot fantasma en el índice 0
        this.addSlot(new GhostSlot(ghostInv, 0, 80, 20));

        // Slots del jugador → índices 1 en adelante
        addPlayerInventory(playerInv);
        addPlayerHotbar(playerInv);
    }

    public ItemStack getChosenItem() {
        return ghostInv.getItem(0);
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        return true; // La validez del menú se mantiene siempre
    }

    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot.hasItem()) {
            ItemStack itemstack1 = slot.getItem();
            itemstack = itemstack1.copy();
            if (index == 0) {
                if (!this.moveItemStackTo(itemstack1, 1, 37, true)) {
                    return ItemStack.EMPTY;
                }

                slot.onQuickCraft(itemstack1, itemstack);
            } else {
                if (this.moveItemStackTo(itemstack1, 0, 1, false)) {
                    return ItemStack.EMPTY;
                }

                if (index >= 1 && index < 28) {
                    if (!this.moveItemStackTo(itemstack1, 28, 37, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (index >= 28 && index < 37) {
                    if (!this.moveItemStackTo(itemstack1, 1, 28, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (!this.moveItemStackTo(itemstack1, 1, 37, false)) {
                    return ItemStack.EMPTY;
                }
            }

            if (itemstack1.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }

            if (itemstack1.getCount() == itemstack.getCount()) {
                return ItemStack.EMPTY;
            }

            slot.onTake(player, itemstack1);
        }

        return itemstack;
    }

    private void addPlayerInventory(Inventory playerInventory) {
        // 27 slots → índices 1..27
        for (int i = 0; i < 3; ++i) {
            for (int l = 0; l < 9; ++l) {
                this.addSlot(new Slot(playerInventory, l + i * 9 + 9, 8 + l * 18, 51 + i * 18));
            }
        }
    }

    private void addPlayerHotbar(Inventory playerInventory) {
        for (int i = 0; i < 9; ++i) {
            this.addSlot(new Slot(playerInventory, i, 8 + i * 18, 109));
        }
    }

    public static class GhostSlot extends Slot {
        public GhostSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return true; // Aceptamos cualquier ítem visualmente
        }

        @Override
        public boolean mayPickup(Player player) {
            return false; // No permitimos recoger el ítem del GhostSlot
        }

        @Override
        public void set(ItemStack stack) {
            if (!stack.isEmpty()) {
                super.set(stack.copyWithCount(1));
            } else {
                super.set(ItemStack.EMPTY);
            }
        }

        @Override
        public ItemStack safeInsert(ItemStack stack, int amount) {
            this.set(stack);
            return ItemStack.EMPTY;
        }
    }
}
