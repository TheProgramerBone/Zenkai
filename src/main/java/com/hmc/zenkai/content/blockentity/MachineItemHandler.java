package com.hmc.zenkai.content.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.IntUnaryOperator;

/**
 * Handler de ranuras de las máquinas de Ki (cámara de contención, máquina de fabricación, infusora) que AGUANTA cambios de
 * distribución.
 *
 * Trampa conocida: {@code ItemStackHandler.deserializeNBT} redimensiona el handler al «Size» guardado. Una máquina colocada
 * antes de un cambio de ranuras carga con las viejas y la ranura nueva revienta por índice (pasó en la 5c al pasar la
 * máquina de fabricación de 19 a 21). Aquí, tras cargar, el handler vuelve al tamaño actual y lo que ya no encaja (sobra
 * por el final, no vale en su ranura o pasa del tope) se aparta a un vertedero que la máquina SUELTA en el mundo en su
 * siguiente tick ({@link #dropSpill}). Nada se pierde ni se queda en una ranura equivocada contando como otra cosa: tras
 * compactar las tolerancias en una ranura (2026-11-23) una tolerancia Divina vieja caía en la ranura de velocidad y,
 * como el tick solo mira el número de ítems, valía por una mejora de velocidad.
 */
public class MachineItemHandler extends ItemStackHandler {

    private final BiPredicate<Integer, ItemStack> valid;
    private final BiPredicate<Integer, ItemStack> keep;
    private final IntUnaryOperator limit;
    private final Runnable changed;
    private final List<ItemStack> spill = new ArrayList<>();

    /**
     * @param valid   qué se puede METER en cada ranura (tolvas, menú)
     * @param keep    qué puede QUEDARSE al cargar (normalmente {@code valid}, más lo que la máquina deja en su salida)
     * @param limit   tope por ranura
     * @param changed aviso de cambio (setChanged o el markDirty de la máquina)
     */
    public MachineItemHandler(int size, BiPredicate<Integer, ItemStack> valid, BiPredicate<Integer, ItemStack> keep,
                              IntUnaryOperator limit, Runnable changed) {
        super(size);
        this.valid = valid;
        this.keep = keep;
        this.limit = limit;
        this.changed = changed;
    }

    private int size() { return getSlots(); }

    @Override protected void onContentsChanged(int slot) { changed.run(); }
    @Override public boolean isItemValid(int slot, @NotNull ItemStack stack) { return valid.test(slot, stack); }
    @Override public int getSlotLimit(int slot) { return limit.applyAsInt(slot); }

    @Override
    public void deserializeNBT(HolderLookup.@NotNull Provider reg, @NotNull CompoundTag nbt) {
        int wanted = size();
        super.deserializeNBT(reg, nbt);
        NonNullList<ItemStack> fixed = NonNullList.withSize(wanted, ItemStack.EMPTY);
        for (int i = 0; i < stacks.size(); i++) {
            ItemStack s = stacks.get(i);
            if (s.isEmpty()) continue;
            if (i >= wanted || !keep.test(i, s)) { spill.add(s); continue; }
            int max = Math.min(s.getMaxStackSize(), limit.applyAsInt(i));
            if (s.getCount() > max) spill.add(s.split(s.getCount() - max));
            fixed.set(i, s);
        }
        stacks = fixed;
    }

    /** Suelta encima de la máquina lo apartado al cargar. Barato: sale al momento si no hay nada. Servidor. */
    public void dropSpill(Level level, BlockPos pos) {
        if (spill.isEmpty()) return;
        for (ItemStack s : spill) Containers.dropItemStack(level, pos.getX(), pos.getY() + 1, pos.getZ(), s);
        spill.clear();
        changed.run();
    }
}
