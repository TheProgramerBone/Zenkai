package com.hmc.zenkai.compat;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

/**
 * Puente opcional con Curios. TODA referencia a clases de Curios vive en la clase interna
 * Impl: si el mod no está, esa clase nunca se carga y no hay NoClassDefFoundError.
 */
public final class CuriosCompat {
    private CuriosCompat() {}

    private static final boolean LOADED = ModList.get().isLoaded("curios");

    public static boolean isLoaded() { return LOADED; }

    /** Primer stack equipado en ese slot de Curios, o ItemStack.EMPTY. */
    public static ItemStack findEquipped(LivingEntity entity, String slotId) {
        if (!LOADED || entity == null) return ItemStack.EMPTY;
        return Impl.findEquipped(entity, slotId);
    }

    private static final class Impl {
        static ItemStack findEquipped(LivingEntity entity, String slotId) {
            return top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(entity)
                    .flatMap(inv -> inv.getStacksHandler(slotId))
                    .map(handler -> {
                        var stacks = handler.getStacks();
                        for (int i = 0; i < stacks.getSlots(); i++) {
                            ItemStack s = stacks.getStackInSlot(i);
                            if (!s.isEmpty()) return s;
                        }
                        return ItemStack.EMPTY;
                    })
                    .orElse(ItemStack.EMPTY);
        }
    }
}