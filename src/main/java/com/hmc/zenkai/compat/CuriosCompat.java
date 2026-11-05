package com.hmc.zenkai.compat;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Puente opcional con Curios. Cualquier referencia a clases de Curios vive en la clase interna
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
     * ICurioStacksHandler.getStacks() devuelve IDynamicStackHandler (extiende
     * IItemHandlerModifiable); si Curios lo cambia, solo hay que tocar esta función.
     */
    public static Optional<IItemHandlerModifiable> handler(LivingEntity entity, String slotId) {
        if (!LOADED || entity == null) return Optional.empty();
        return Impl.handler(entity, slotId);
    }


    /** Un objeto equipado en Curios con su ranura (id del tipo + posición dentro del tipo). Solo tipos de vanilla. */
    public record Equipped(String id, int index, ItemStack stack) {}

    /** Todo lo que lleva equipado en Curios (copias). Vacía si Curios no está. */
    public static List<Equipped> allEquipped(LivingEntity entity) {
        if (!LOADED || entity == null) return List.of();
        return Impl.allEquipped(entity);
    }

    /** Sprite (del atlas de bloques) del icono de una ranura de Curios, o null si Curios no está. */
    public static net.minecraft.resources.ResourceLocation slotIcon(String slotId) {
        return LOADED ? Impl.slotIcon(slotId) : null;
    }

    /** TODAS las ranuras de Curios de la entidad, también las vacías (la pila puede ser EMPTY): para que el cuerpo enseñe cada hueco con su icono. */
    public static List<Equipped> allSlots(LivingEntity entity) {
        if (!LOADED || entity == null) return List.of();
        return Impl.allSlots(entity);
    }

    /** ¿Vale ese objeto para una ranura de ese tipo en esa entidad? (false si Curios no está). */
    public static boolean isValidFor(LivingEntity entity, String slotId, ItemStack stack) {
        return LOADED && entity != null && Impl.isValidFor(entity, slotId, stack);
    }

    /** Equipa en la primera ranura LIBRE y válida de ese tipo. Devuelve lo que no cupo (EMPTY si se equipó). */
    public static ItemStack tryEquip(LivingEntity entity, String slotId, ItemStack stack) {
        if (!LOADED || entity == null || stack.isEmpty()) return stack;
        return Impl.tryEquip(entity, slotId, stack);
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

        static List<Equipped> allEquipped(LivingEntity entity) {
            List<Equipped> out = new ArrayList<>();
            top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(entity).ifPresent(inv -> {
                for (var en : inv.getCurios().entrySet()) {
                    var stacks = en.getValue().getStacks();
                    for (int i = 0; i < stacks.getSlots(); i++) {
                        ItemStack s = stacks.getStackInSlot(i);
                        if (!s.isEmpty()) out.add(new Equipped(en.getKey(), i, s.copy()));
                    }
                }
            });
            return out;
        }

        static List<Equipped> allSlots(LivingEntity entity) {
            List<Equipped> out = new ArrayList<>();
            top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(entity).ifPresent(inv -> {
                for (var en : inv.getCurios().entrySet()) {
                    var stacks = en.getValue().getStacks();
                    for (int i = 0; i < stacks.getSlots(); i++) out.add(new Equipped(en.getKey(), i, stacks.getStackInSlot(i).copy()));
                }
            });
            return out;
        }

        static net.minecraft.resources.ResourceLocation slotIcon(String slotId) {
            return top.theillusivec4.curios.api.CuriosApi.getSlotIcon(slotId);
        }

        static boolean isValidFor(LivingEntity entity, String slotId, ItemStack stack) {
            return top.theillusivec4.curios.api.CuriosApi.isStackValid(
                    new top.theillusivec4.curios.api.SlotContext(slotId, entity, 0, false, true), stack);
        }

        static ItemStack tryEquip(LivingEntity entity, String slotId, ItemStack stack) {
            var h = top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(entity)
                    .flatMap(inv -> inv.getStacksHandler(slotId));
            if (h.isEmpty()) return stack;
            var stacks = h.get().getStacks();
            for (int i = 0; i < stacks.getSlots(); i++) {
                if (!stacks.getStackInSlot(i).isEmpty()) continue;
                if (!top.theillusivec4.curios.api.CuriosApi.isStackValid(
                        new top.theillusivec4.curios.api.SlotContext(slotId, entity, i, false, true), stack)) return stack;
                stacks.setStackInSlot(i, stack.copyWithCount(1));
                ItemStack rest = stack.copy();
                rest.shrink(1);
                return rest.isEmpty() ? ItemStack.EMPTY : tryEquip(entity, slotId, rest);
            }
            return stack;
        }
    }
}