package com.hmc.zenkai.client.gui.menu;

import com.hmc.zenkai.content.blockentity.KiContainmentChamberBlockEntity;
import com.hmc.zenkai.content.blockentity.KiMachineBlockEntity;
import com.hmc.zenkai.feature.kichamber.KiEnergyType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiPredicate;
import java.util.function.IntUnaryOperator;

/**
 * Base de los menús de los talleres de armadura (Fabricadora y Estación de imbuido, separadas el 2026-11-23): las cuatro
 * ranuras de mejora en la columna derecha, el inventario del jugador, la lectura de FE y tanque (los nueve primeros
 * huecos de ContainerData, {@link KiMachineBlockEntity#sharedData}) y el shift-clic. Cada menú pone sus ranuras propias
 * y decide a dónde va un shift-clic del inventario ({@link #moveIntoMachine}).
 *
 * Las ranuras de una pestaña oculta se desactivan con Slot.isActive: ni se dibujan ni se pueden tocar. La pestaña vive en
 * el menú y se cambia en los DOS lados (el cliente la pone al pulsar, el servidor al recibir el botón vanilla).
 */
public abstract class KiMachineMenu extends AbstractContainerMenu {

    public static final int D_FE_LO = 0, D_FE_HI = 1, D_FE_CAP_LO = 2, D_FE_CAP_HI = 3;
    public static final int D_TANK_LO = 4, D_TANK_HI = 5, D_CAP_LO = 6, D_CAP_HI = 7, D_TANK_TYPE = 8;

    // ── Layout común (== tools/gen_armor_fabricator_screen.py) ───────────────
    public static final int UPG_X = 226, UPG_Y = 32, STEP = 18;
    private static final int INV_X = 47, INV_Y = 170, HOTBAR_Y = 229;

    protected final ContainerData data;
    protected final ContainerLevelAccess access;
    private final BlockPos pos;
    private final int machineSlots;
    private final BiPredicate<Integer, ItemStack> valid;
    private final IntUnaryOperator limit;
    protected int tab;

    protected KiMachineMenu(@Nullable MenuType<?> type, int id, ContainerData data, ContainerLevelAccess access, BlockPos pos,
                             int machineSlots, BiPredicate<Integer, ItemStack> valid, IntUnaryOperator limit) {
        super(type, id);
        this.data = data;
        this.access = access;
        this.pos = pos;
        this.machineSlots = machineSlots;
        this.valid = valid;
        this.limit = limit;
    }

    protected void addUpgradeSlots(IItemHandler machine) {
        for (int i = 0; i < KiMachineBlockEntity.UPGRADE_SLOTS; i++) addMachineSlot(machine, i, UPG_X, UPG_Y + i * STEP, -1);
    }

    /** Ranura de máquina; {@code onTab} = -1 siempre visible, o la pestaña en la que existe. */
    protected void addMachineSlot(IItemHandler machine, int slot, int x, int y, int onTab) {
        addSlot(new SlotItemHandler(machine, slot, x, y) {
            @Override public boolean mayPlace(@NotNull ItemStack stack) { return valid.test(slot, stack); }
            @Override public int getMaxStackSize() { return limit.applyAsInt(slot); }
            @Override public int getMaxStackSize(@NotNull ItemStack stack) {
                return Math.min(stack.getMaxStackSize(), limit.applyAsInt(slot));
            }
            @Override public boolean isActive() { return onTab < 0 || onTab == tab; }
        });
    }

    protected void addPlayerInventory(Inventory inv) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) addSlot(new Slot(inv, col + row * 9 + 9, INV_X + col * 18, INV_Y + row * 18));
        }
        for (int col = 0; col < 9; col++) addSlot(new Slot(inv, col, INV_X + col * 18, HOTBAR_Y));
    }

    public BlockPos pos() { return pos; }
    public int tab() { return tab; }

    // ── Lectura ──────────────────────────────────────────────────────────────
    protected int wide(int lo, int hi) { return ((data.get(hi) & 0xFFFF) << 16) | (data.get(lo) & 0xFFFF); }

    public int energy() { return wide(D_FE_LO, D_FE_HI); }
    public int energyCapacity() { return Math.max(1, wide(D_FE_CAP_LO, D_FE_CAP_HI)); }
    public int tankAmount() { return wide(D_TANK_LO, D_TANK_HI); }
    public int tankCapacity() { return Math.max(1, wide(D_CAP_LO, D_CAP_HI)); }
    @Nullable public KiEnergyType tankType() { return KiEnergyType.byOrdinal(data.get(D_TANK_TYPE)); }
    public float energyFraction() { return Math.min(1f, energy() / (float) energyCapacity()); }
    public float tankFraction() { return Math.min(1f, tankAmount() / (float) tankCapacity()); }

    public boolean tolerates(KiEnergyType type) {
        return KiContainmentChamberBlockEntity.tolerates(this.slots.get(KiContainmentChamberBlockEntity.SLOT_TOLERANCE).getItem(), type);
    }

    /** Fluido de Ki que cuesta de verdad {@code mb} con las mejoras de eficiencia puestas (misma cuenta que el servidor). */
    public long fluidCost(long mb) {
        return KiMachineBlockEntity.discountedMb(mb, machineStack(KiMachineBlockEntity.SLOT_EFFICIENCY).getCount());
    }

    public ItemStack machineStack(int slot) { return this.slots.get(slot).getItem(); }

    /** Shift-clic de un stack del inventario hacia las ranuras propias de la máquina (sin las mejoras). */
    protected abstract boolean moveIntoMachine(ItemStack stack);

    /** Shift-clic. De la máquina al inventario siempre. Del inventario: mejoras a su ranura y el resto, lo que diga el menú. */
    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int invFirst = machineSlots, invEnd = machineSlots + 36;

        if (index < machineSlots) {
            if (!moveItemStackTo(stack, invFirst, invEnd, true)) return ItemStack.EMPTY;
        } else {
            boolean moved = false;
            for (int i = 0; i < KiMachineBlockEntity.UPGRADE_SLOTS && !moved; i++) {
                if (valid.test(i, stack)) moved = moveItemStackTo(stack, i, i + 1, false);
            }
            if (!moved) moved = moveIntoMachine(stack);
            if (!moved) return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        if (stack.getCount() == original.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, stack);
        return original;
    }
}
