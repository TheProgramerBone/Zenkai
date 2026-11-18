package com.hmc.zenkai.util;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;

/** Utilidades de ItemStackHandler. */
public final class ItemHandlers {
    private ItemHandlers() {}

    /**
     * Carga un handler SIN cambiar su número de ranuras. ItemStackHandler.deserializeNBT llama a
     * setSize con el «Size» guardado: un mundo de antes de ampliar las ranuras (el generador pasó de 1 a 3
     * de carga y el tanque de energía de 2 a 6, 2026-11-17) dejaría el handler pequeño, y el menú, que
     * añade las ranuras nuevas, rompería con un índice fuera de rango.
     */
    public static void loadKeepingSize(ItemStackHandler handler, HolderLookup.Provider reg, CompoundTag tag) {
        int size = handler.getSlots();
        handler.deserializeNBT(reg, tag);
        if (handler.getSlots() == size) return;
        ItemStack[] kept = new ItemStack[Math.min(size, handler.getSlots())];
        for (int i = 0; i < kept.length; i++) kept[i] = handler.getStackInSlot(i);
        handler.setSize(size);
        for (int i = 0; i < kept.length; i++) handler.setStackInSlot(i, kept[i]);
    }
}
