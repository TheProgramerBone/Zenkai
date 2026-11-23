package com.hmc.zenkai.client.gui.menu;

import com.hmc.zenkai.client.gui.ModMenuTypes;
import com.hmc.zenkai.content.blockentity.KiContainmentChamberBlockEntity;
import com.hmc.zenkai.content.blockentity.KiInfuserBlockEntity;
import com.hmc.zenkai.feature.kichamber.KiEnergyType;
import com.hmc.zenkai.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Menú de la infusora de Ki (Fase 5c). Ranuras en this.slots en el orden de KiInfuserBlockEntity:
 *   0..5 mejoras · 6 entrada · 7 salida · 8..43 inventario y hotbar.
 * LAS COORDENADAS SON PARTE DEL ASSET: cada ranura tiene su pozo en ki_infuser.png (tools/gen_ki_infuser_screen.py).
 * Cifras grandes partidas en dos huecos de 16 bits, como ArmorFabricatorMenu.
 */
public class KiInfuserMenu extends AbstractContainerMenu {

    public static final int D_FE_LO = 0, D_FE_HI = 1, D_FE_CAP_LO = 2, D_FE_CAP_HI = 3;
    public static final int D_TANK_LO = 4, D_TANK_HI = 5, D_CAP_LO = 6, D_CAP_HI = 7;
    public static final int D_TANK_TYPE = 8, D_PROGRESS = 9, D_WORKING = 10;
    public static final int DATA_SLOTS = 11;

    public static final int MACHINE_SLOTS = KiInfuserBlockEntity.SLOTS;
    public static final int INV_FIRST = MACHINE_SLOTS, INV_END = MACHINE_SLOTS + 36;

    // ── Layout (== tools/gen_ki_infuser_screen.py) ───────────────────────────
    public static final int UPG_X = 226, UPG_Y = 32, STEP = 18;
    public static final int IN_X = 92, IN_Y = 52, OUT_X = 156, OUT_Y = 52;
    private static final int INV_X = 47, INV_Y = 170, HOTBAR_Y = 229;

    private final ContainerData data;
    private final ContainerLevelAccess access;
    private final BlockPos pos;

    public KiInfuserMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(id, inv, new ItemStackHandler(MACHINE_SLOTS), new SimpleContainerData(DATA_SLOTS),
                ContainerLevelAccess.NULL, buf.readBlockPos());
    }

    public KiInfuserMenu(int id, Inventory inv, KiInfuserBlockEntity be) {
        this(id, inv, be.items(), be.containerData(),
                ContainerLevelAccess.create(Objects.requireNonNull(be.getLevel()), be.getBlockPos()), be.getBlockPos());
    }

    private KiInfuserMenu(int id, Inventory inv, IItemHandler machine, ContainerData data, ContainerLevelAccess access,
                          BlockPos pos) {
        super(ModMenuTypes.KI_INFUSER.get(), id);
        this.data = data;
        this.access = access;
        this.pos = pos;

        for (int i = 0; i < KiInfuserBlockEntity.UPGRADE_SLOTS; i++) addMachineSlot(machine, i, UPG_X, UPG_Y + i * STEP);
        addMachineSlot(machine, KiInfuserBlockEntity.SLOT_INPUT, IN_X, IN_Y);
        addMachineSlot(machine, KiInfuserBlockEntity.SLOT_OUTPUT, OUT_X, OUT_Y);

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) addSlot(new Slot(inv, col + row * 9 + 9, INV_X + col * 18, INV_Y + row * 18));
        }
        for (int col = 0; col < 9; col++) addSlot(new Slot(inv, col, INV_X + col * 18, HOTBAR_Y));
        addDataSlots(data);
    }

    private void addMachineSlot(IItemHandler machine, int slot, int x, int y) {
        addSlot(new SlotItemHandler(machine, slot, x, y) {
            @Override public boolean mayPlace(@NotNull ItemStack stack) { return KiInfuserBlockEntity.validFor(slot, stack); }
            @Override public int getMaxStackSize() { return KiInfuserBlockEntity.slotLimit(slot); }
            @Override public int getMaxStackSize(@NotNull ItemStack stack) {
                return Math.min(stack.getMaxStackSize(), KiInfuserBlockEntity.slotLimit(slot));
            }
        });
    }

    public BlockPos pos() { return pos; }

    // ── Lectura ──────────────────────────────────────────────────────────────
    private int wide(int lo, int hi) { return ((data.get(hi) & 0xFFFF) << 16) | (data.get(lo) & 0xFFFF); }

    public int energy() { return wide(D_FE_LO, D_FE_HI); }
    public int energyCapacity() { return Math.max(1, wide(D_FE_CAP_LO, D_FE_CAP_HI)); }
    public int tankAmount() { return wide(D_TANK_LO, D_TANK_HI); }
    public int tankCapacity() { return Math.max(1, wide(D_CAP_LO, D_CAP_HI)); }
    @Nullable public KiEnergyType tankType() { return KiEnergyType.byOrdinal(data.get(D_TANK_TYPE)); }
    public int progress() { return data.get(D_PROGRESS); }
    public boolean isWorking() { return data.get(D_WORKING) != 0; }
    public float energyFraction() { return Math.min(1f, energy() / (float) energyCapacity()); }
    public float tankFraction() { return Math.min(1f, tankAmount() / (float) tankCapacity()); }
    public float progressFraction() {
        return Math.min(1f, progress() / (float) com.hmc.zenkai.feature.armor.KiInfusion.TICKS);
    }

    public boolean tolerates(KiEnergyType type) {
        int slot = KiContainmentChamberBlockEntity.toleranceSlot(type);
        return slot < 0 || this.slots.get(slot).hasItem();
    }

    public ItemStack machineStack(int slot) { return this.slots.get(slot).getItem(); }

    @Override
    public boolean stillValid(@NotNull Player player) {
        return stillValid(access, player, ModBlocks.KI_INFUSER.get());
    }

    /** Shift-clic: de la máquina al inventario; del inventario, mejoras a su ranura y lo infundible a la entrada. */
    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();

        if (index < MACHINE_SLOTS) {
            if (!moveItemStackTo(stack, INV_FIRST, INV_END, true)) return ItemStack.EMPTY;
        } else {
            boolean moved = false;
            for (int i = 0; i < KiInfuserBlockEntity.UPGRADE_SLOTS && !moved; i++) {
                if (KiInfuserBlockEntity.validFor(i, stack)) moved = moveItemStackTo(stack, i, i + 1, false);
            }
            if (!moved && KiInfuserBlockEntity.canInfuse(stack)) {
                moved = moveItemStackTo(stack, KiInfuserBlockEntity.SLOT_INPUT, KiInfuserBlockEntity.SLOT_INPUT + 1, false);
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
