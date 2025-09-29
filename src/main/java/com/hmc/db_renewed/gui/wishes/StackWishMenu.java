package com.hmc.db_renewed.gui.wishes;

import com.hmc.db_renewed.config.WishConfig;
import com.hmc.db_renewed.gui.ModMenuTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
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

    // --- Helpers públicos para que handlers puedan leer/poner el ghost item sin
    // recurrir al payload cliente (importante para seguridad) ---
    public ItemStack getChosenItem() {
        return ghostInv.getItem(0);
    }

    public void setChosenItem(ItemStack stack) {
        if (stack == null) stack = ItemStack.EMPTY;
        // Aseguramos copia con NBT y al menos 1
        if (!stack.isEmpty()) {
            ItemStack copy = stack.copy();
            copy.setCount(1);
            ghostInv.setItem(0, copy);
        } else {
            ghostInv.setItem(0, ItemStack.EMPTY);
        }
        this.broadcastChanges();
    }

    public void clearChosenItem() {
        ghostInv.setItem(0, ItemStack.EMPTY);
        this.broadcastChanges();
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        return true; // La validez del menú se mantiene siempre
    }

    /**
     * SHIFT+CLICK: en lugar de mover el stack real hacia el ghost slot,
     * clonamos 1 del item y lo ponemos en el ghost slot. No consumimos el item real.
     */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        if (index < 0 || index >= this.slots.size()) return ItemStack.EMPTY;

        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack stackInSlot = slot.getItem();
            result = stackInSlot.copy();

            if (index == 0) {
                // No movemos desde el ghost slot hacia el inventario
                return ItemStack.EMPTY;
            } else {
                // Copiar 1 al ghost slot sin consumir el original
                ItemStack ghostCopy = stackInSlot.copy();
                ghostCopy.setCount(1);
                this.ghostInv.setItem(0, ghostCopy);
                this.broadcastChanges();
                return ItemStack.EMPTY;
            }
        }
        return result;
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
            if (stack == null || stack.isEmpty()) return false;
            // Validación rápida en cliente/servidor: si está baneado, no permitir colocarlo.
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (id == null) return true; // fallback: permitir
            return !WishConfig.getBannedItems().contains(id);
        }

        @Override
        public boolean mayPickup(Player player) {
            // Permitimos "sacar" el item visualmente para poder limpiarlo del ghost slot,
            // pero no daremos una copia real al jugador (ver onTake).
            return true;
        }

        @Override
        public void set(ItemStack stack) {
            if (stack != null && !stack.isEmpty()) {
                ItemStack copy = stack.copy();
                copy.setCount(1);
                super.set(copy);
            } else {
                super.set(ItemStack.EMPTY);
            }
        }

        @Override
        public ItemStack safeInsert(ItemStack stack, int amount) {
            // Usado en algunos flujos: ponemos la copia y no retornamos nada
            this.set(stack);
            return ItemStack.EMPTY;
        }

        @Override
        public void onTake(Player player, ItemStack stack) {
            // Cuando el jugador "toma" el item del ghost slot, lo que queremos
            // es **limpiar** el ghost slot, no darle una copia real del item.
            super.set(ItemStack.EMPTY);
            // No hacemos nada con el inventario del jugador.
        }
    }
}