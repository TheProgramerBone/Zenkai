package com.hmc.zenkai.compat;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

import java.util.Optional;

/**
 * Puente opcional con Curios. TODA referencia a clases de Curios vive en la clase interna
 * Impl: si el mod no está, esa clase nunca se carga y no hay NoClassDefFoundError.
 * Por eso handler() devuelve IItemHandlerModifiable, que es de NeoForge, y no el
 * ICurioStacksHandler de Curios: en cuanto un tipo de Curios aparece en una firma pública,
 * cargar esta clase arrastra el mod y el puente deja de ser opcional.
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

    /**
     * Handler del hueco, para engancharle un Slot de menú. Vacío si Curios no está o si la
     * entidad no tiene ese hueco.
     * ⚠ API a verificar: ICurioStacksHandler.getStacks() devuelve IDynamicStackHandler, que
     * extiende IItemHandlerModifiable. Si en tu versión de Curios cambió, es lo único que hay
     * que tocar y solo aquí dentro.
     */
    public static Optional<IItemHandlerModifiable> handler(LivingEntity entity, String slotId) {
        if (!LOADED || entity == null) return Optional.empty();
        return Impl.handler(entity, slotId);
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

        static Optional<IItemHandlerModifiable> handler(LivingEntity entity, String slotId) {
            return top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(entity)
                    .flatMap(inv -> inv.getStacksHandler(slotId))
                    .map(h -> (IItemHandlerModifiable) h.getStacks());
        }
    }
}