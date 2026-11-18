package com.hmc.zenkai.client.gui.menu;

import com.hmc.zenkai.client.gui.ModMenuTypes;
import com.hmc.zenkai.content.block.TransportBlock;
import com.hmc.zenkai.content.blockentity.EnergyTransportBlockEntity;
import com.hmc.zenkai.content.blockentity.FluidTransportBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.jetbrains.annotations.NotNull;

/**
 * Menú de los TANQUES de energía y de fluidos (0.7.0). Uno solo para los dos: lo que cambia son las
 * ranuras (energía: cargar y descargar; fluido: llenar entrada/salida y vaciar entrada/salida) y qué
 * cifras trae el ContainerData. El tipo viaja en el buffer de apertura junto a la posición.
 *
 * Las cifras son las del GRUPO (un 2x2x3 se lee como un tanque) y viajan partidas en 16 bits: el
 * paquete de ContainerData de vanilla manda cada valor como short (ver KiChamberMenu).
 * Geometría de ranuras == tools/gen_tank_screen.py (pozos copiados del inventario de la base).
 */
public class TankMenu extends AbstractContainerMenu {

    public static final int D_AMOUNT_LO = 0, D_AMOUNT_HI = 1, D_CAP_LO = 2, D_CAP_HI = 3, D_SIZE = 4, D_FLUID = 5;

    /** Ranuras de máquina, en coordenadas del PNG (esquina del ítem, 1 px dentro del pozo). */
    public static final int SLOT_IN_X = 48, SLOT_OUT_X = 92, SLOT_ROW1_Y = 46, SLOT_ROW2_Y = 90;
    private static final int INV_X = 47, INV_Y = 170, HOTBAR_Y = 229;   // == KiChamberMenu
    /** Tanque de energía: filas de 3 con el paso de la rejilla del generador (== EnergyGeneratorMenu FUEL_X/FUEL_STEP). */
    public static final int ENERGY_SLOT_X = 46, ENERGY_SLOT_STEP = 22;
    public static int energySlotX(int i) { return ENERGY_SLOT_X + i * ENERGY_SLOT_STEP; }

    private final boolean energy;
    private final int machineSlots;
    private final ContainerData data;
    private final ContainerLevelAccess access;
    private final BlockPos pos;

    /** Cliente. */
    public TankMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(id, inv, buf.readBlockPos(), buf.readBoolean(), null, null, ContainerLevelAccess.NULL);
    }

    public TankMenu(int id, Inventory inv, EnergyTransportBlockEntity be) {
        this(id, inv, be.getBlockPos(), true, be.items(), be.containerData(),
                ContainerLevelAccess.create(be.getLevel(), be.getBlockPos()));
    }

    public TankMenu(int id, Inventory inv, FluidTransportBlockEntity be) {
        this(id, inv, be.getBlockPos(), false, be.items(), be.containerData(),
                ContainerLevelAccess.create(be.getLevel(), be.getBlockPos()));
    }

    private TankMenu(int id, Inventory inv, BlockPos pos, boolean energy, IItemHandler items, ContainerData data,
                     ContainerLevelAccess access) {
        super(ModMenuTypes.TANK.get(), id);
        this.pos = pos;
        this.energy = energy;
        this.machineSlots = energy ? EnergyTransportBlockEntity.SLOTS : FluidTransportBlockEntity.SLOTS;
        IItemHandler handler = items != null ? items : new ItemStackHandler(machineSlots);
        this.data = data != null ? data : new SimpleContainerData(6);
        this.access = access;

        if (energy) {
            for (int i = 0; i < EnergyTransportBlockEntity.ROW; i++) {
                addSlot(machineSlot(handler, EnergyTransportBlockEntity.SLOT_CHARGE + i, energySlotX(i), SLOT_ROW1_Y, true));
            }
            for (int i = 0; i < EnergyTransportBlockEntity.ROW; i++) {
                addSlot(machineSlot(handler, EnergyTransportBlockEntity.SLOT_DISCHARGE + i, energySlotX(i), SLOT_ROW2_Y, true));
            }
        } else {
            addSlot(machineSlot(handler, FluidTransportBlockEntity.SLOT_FILL_IN, SLOT_IN_X, SLOT_ROW1_Y, true));
            addSlot(machineSlot(handler, FluidTransportBlockEntity.SLOT_FILL_OUT, SLOT_OUT_X, SLOT_ROW1_Y, false));
            addSlot(machineSlot(handler, FluidTransportBlockEntity.SLOT_DRAIN_IN, SLOT_IN_X, SLOT_ROW2_Y, true));
            addSlot(machineSlot(handler, FluidTransportBlockEntity.SLOT_DRAIN_OUT, SLOT_OUT_X, SLOT_ROW2_Y, false));
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) addSlot(new Slot(inv, col + row * 9 + 9, INV_X + col * 18, INV_Y + row * 18));
        }
        for (int col = 0; col < 9; col++) addSlot(new Slot(inv, col, INV_X + col * 18, HOTBAR_Y));
        addDataSlots(this.data);
    }

    /** El filtro va también en el Slot: en el cliente el handler es uno vacío sin reglas. */
    private Slot machineSlot(IItemHandler h, int index, int x, int y, boolean input) {
        return new SlotItemHandler(h, index, x, y) {
            @Override public boolean mayPlace(@NotNull ItemStack stack) {
                if (!input) return false;   // las salidas solo se vacían
                return energy ? EnergyTransportBlockEntity.validFor(stack) : FluidTransportBlockEntity.validFor(stack);
            }
            @Override public int getMaxStackSize() { return energy ? 1 : 64; }
        };
    }

    public boolean isEnergy() { return energy; }
    public BlockPos pos() { return pos; }

    private int wide(int lo, int hi) { return ((data.get(hi) & 0xFFFF) << 16) | (data.get(lo) & 0xFFFF); }

    public int amount()   { return wide(D_AMOUNT_LO, D_AMOUNT_HI); }
    public int capacity() { return Math.max(1, wide(D_CAP_LO, D_CAP_HI)); }
    public int groupSize() { return Math.max(1, data.get(D_SIZE)); }
    public float fraction() { return Math.min(1f, amount() / (float) capacity()); }

    /** Fluido del grupo (Fluids.EMPTY si no hay o es un tanque de energía). */
    public Fluid fluid() {
        if (energy) return Fluids.EMPTY;
        int id = (short) data.get(D_FLUID);   // -1 llega como 0xFFFF: se recupera el signo
        return id < 0 ? Fluids.EMPTY : BuiltInRegistries.FLUID.byId(id);
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        return access.evaluate((level, p) -> level.getBlockState(p).getBlock() instanceof TransportBlock tb
                && !tb.isCable() && player.distanceToSqr(p.getCenter()) <= 64.0, true);
    }

    /** Shift-clic: de la máquina al inventario; del inventario a la primera ENTRADA que lo acepte. */
    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int invFirst = machineSlots, invEnd = machineSlots + 36;
        if (index < machineSlots) {
            if (!moveItemStackTo(stack, invFirst, invEnd, true)) return ItemStack.EMPTY;
        } else {
            boolean moved = false;
            for (int i = 0; i < machineSlots && !moved; i++) {
                if (slots.get(i).mayPlace(stack)) moved = moveItemStackTo(stack, i, i + 1, false);
            }
            if (!moved) return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        if (stack.getCount() == original.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, stack);
        return original;
    }
}
