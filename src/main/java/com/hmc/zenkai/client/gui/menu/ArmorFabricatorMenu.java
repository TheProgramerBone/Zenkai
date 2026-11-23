package com.hmc.zenkai.client.gui.menu;

import com.hmc.zenkai.client.gui.ModMenuTypes;
import com.hmc.zenkai.content.blockentity.ArmorFabricatorBlockEntity;
import com.hmc.zenkai.content.item.BloodItem;
import com.hmc.zenkai.feature.armor.ArmorSets;
import com.hmc.zenkai.feature.armor.AttributeTemplates;
import com.hmc.zenkai.feature.kichamber.KiEnergyType;
import com.hmc.zenkai.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
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
 * Menú de la máquina de fabricación (Fase 5b). Ranuras en this.slots, en el orden de ArmorFabricatorBlockEntity:
 *   0..5 mejoras (siempre visibles) · 6 patrón · 7..15 material · 16 salida (pestaña FABRICAR)
 *   17 pieza · 18 sangre · 19 plantilla · 20 pieza destino (pestaña ATRIBUTOS) · 21..56 inventario y hotbar.
 * Las ranuras de la pestaña oculta se desactivan con Slot.isActive: ni se dibujan ni se pueden tocar. La pestaña vive
 * en el menú y se cambia en los DOS lados (el cliente la pone al pulsar, el servidor al recibir el botón vanilla).
 * LAS COORDENADAS SON PARTE DEL ASSET: cada ranura tiene su pozo en armor_fabricator_*.png
 * (tools/gen_armor_fabricator_screen.py). Cifras grandes partidas en dos huecos, como KiChamberMenu.
 */
public class ArmorFabricatorMenu extends AbstractContainerMenu {

    public static final int D_FE_LO = 0, D_FE_HI = 1, D_FE_CAP_LO = 2, D_FE_CAP_HI = 3;
    public static final int D_TANK_LO = 4, D_TANK_HI = 5, D_CAP_LO = 6, D_CAP_HI = 7;
    public static final int D_TANK_TYPE = 8, D_PROGRESS = 9, D_MAX_PROGRESS = 10, D_FLAGS = 11, D_PIECE = 12;
    public static final int D_IMBUE_PROGRESS = 13;
    public static final int DATA_SLOTS = 14;
    public static final int F_WORKING = 1, F_TURBO = 2, F_ENABLED = 4;

    /** Botones: 0..3 pieza, 4 imbuir, 5/6 pestañas, 7 interruptor de la fabricación. */
    public static final int BTN_PIECE_FIRST = 0, BTN_IMBUE = 4, BTN_TAB_CRAFT = 5, BTN_TAB_ATTR = 6, BTN_TOGGLE_CRAFT = 7;
    public static final int TAB_CRAFT = 0, TAB_ATTR = 1;

    public static final int MACHINE_SLOTS = ArmorFabricatorBlockEntity.SLOTS;
    public static final int INV_FIRST = MACHINE_SLOTS, INV_END = MACHINE_SLOTS + 36;

    // ── Layout (== tools/gen_armor_fabricator_screen.py) ─────────────────────
    public static final int UPG_X = 226, UPG_Y = 32, STEP = 18;
    public static final int PATTERN_X = 66, PATTERN_Y = 34;
    public static final int MAT_X = 66, MAT_Y = 58;
    public static final int OUT_X = 166, OUT_Y = 76;
    public static final int ARMOR_X = 66, ARMOR_Y = 36, BLOOD_X = 66, BLOOD_Y = 64;
    /** Fase 5c: arriba pieza → destino (copiar/transferir); abajo sangre y plantilla. */
    public static final int TARGET_X = 96, TARGET_Y = 36, TEMPLATE_X = 96, TEMPLATE_Y = 64;
    private static final int INV_X = 47, INV_Y = 170, HOTBAR_Y = 229;

    private final ContainerData data;
    private final ContainerLevelAccess access;
    private final BlockPos pos;
    @Nullable private final ArmorFabricatorBlockEntity be;
    private int tab = TAB_CRAFT;

    public ArmorFabricatorMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(id, inv, null, new ItemStackHandler(MACHINE_SLOTS), new SimpleContainerData(DATA_SLOTS),
                ContainerLevelAccess.NULL, buf.readBlockPos());
        setTab(buf.readVarInt());   // la pestaña guardada en la máquina (ArmorFabricatorBlock la escribe tras la posición)
    }

    public ArmorFabricatorMenu(int id, Inventory inv, ArmorFabricatorBlockEntity be) {
        this(id, inv, be, be.items(), be.containerData(),
                ContainerLevelAccess.create(Objects.requireNonNull(be.getLevel()), be.getBlockPos()), be.getBlockPos());
        setTab(be.lastTab());
    }

    private ArmorFabricatorMenu(int id, Inventory inv, @Nullable ArmorFabricatorBlockEntity be, IItemHandler machine,
                                ContainerData data, ContainerLevelAccess access, BlockPos pos) {
        super(ModMenuTypes.ARMOR_FABRICATOR.get(), id);
        this.be = be;
        this.data = data;
        this.access = access;
        this.pos = pos;

        for (int i = 0; i < ArmorFabricatorBlockEntity.UPGRADE_SLOTS; i++) addMachineSlot(machine, i, UPG_X, UPG_Y + i * STEP, -1);
        addMachineSlot(machine, ArmorFabricatorBlockEntity.SLOT_PATTERN, PATTERN_X, PATTERN_Y, TAB_CRAFT);
        for (int i = 0; i < ArmorFabricatorBlockEntity.MATERIAL_SLOTS; i++) {
            addMachineSlot(machine, ArmorFabricatorBlockEntity.SLOT_MATERIAL_FIRST + i,
                    MAT_X + (i % 3) * STEP, MAT_Y + (i / 3) * STEP, TAB_CRAFT);
        }
        addMachineSlot(machine, ArmorFabricatorBlockEntity.SLOT_OUTPUT, OUT_X, OUT_Y, TAB_CRAFT);
        addMachineSlot(machine, ArmorFabricatorBlockEntity.SLOT_ARMOR, ARMOR_X, ARMOR_Y, TAB_ATTR);
        addMachineSlot(machine, ArmorFabricatorBlockEntity.SLOT_BLOOD, BLOOD_X, BLOOD_Y, TAB_ATTR);
        addMachineSlot(machine, ArmorFabricatorBlockEntity.SLOT_TEMPLATE, TEMPLATE_X, TEMPLATE_Y, TAB_ATTR);
        addMachineSlot(machine, ArmorFabricatorBlockEntity.SLOT_TARGET, TARGET_X, TARGET_Y, TAB_ATTR);

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) addSlot(new Slot(inv, col + row * 9 + 9, INV_X + col * 18, INV_Y + row * 18));
        }
        for (int col = 0; col < 9; col++) addSlot(new Slot(inv, col, INV_X + col * 18, HOTBAR_Y));
        addDataSlots(data);
    }

    /** Ranura de máquina; {@code onTab} = -1 siempre visible, o la pestaña en la que existe. */
    private void addMachineSlot(IItemHandler machine, int slot, int x, int y, int onTab) {
        addSlot(new SlotItemHandler(machine, slot, x, y) {
            @Override public boolean mayPlace(@NotNull ItemStack stack) { return ArmorFabricatorBlockEntity.validFor(slot, stack); }
            @Override public int getMaxStackSize() { return ArmorFabricatorBlockEntity.slotLimit(slot); }
            @Override public int getMaxStackSize(@NotNull ItemStack stack) {
                return Math.min(stack.getMaxStackSize(), ArmorFabricatorBlockEntity.slotLimit(slot));
            }
            @Override public boolean isActive() { return onTab < 0 || onTab == tab; }
        });
    }

    public BlockPos pos() { return pos; }
    public int tab() { return tab; }
    public void setTab(int t) { tab = t == TAB_ATTR ? TAB_ATTR : TAB_CRAFT; }

    // ── Lectura ──────────────────────────────────────────────────────────────
    private int wide(int lo, int hi) { return ((data.get(hi) & 0xFFFF) << 16) | (data.get(lo) & 0xFFFF); }

    public int energy() { return wide(D_FE_LO, D_FE_HI); }
    public int energyCapacity() { return Math.max(1, wide(D_FE_CAP_LO, D_FE_CAP_HI)); }
    public int tankAmount() { return wide(D_TANK_LO, D_TANK_HI); }
    public int tankCapacity() { return Math.max(1, wide(D_CAP_LO, D_CAP_HI)); }
    @Nullable public KiEnergyType tankType() { return KiEnergyType.byOrdinal(data.get(D_TANK_TYPE)); }
    public int progress() { return data.get(D_PROGRESS); }
    public int maxProgress() { return data.get(D_MAX_PROGRESS); }
    public boolean isWorking() { return (data.get(D_FLAGS) & F_WORKING) != 0; }
    public boolean isTurbo() { return (data.get(D_FLAGS) & F_TURBO) != 0; }
    public boolean craftEnabled() { return (data.get(D_FLAGS) & F_ENABLED) != 0; }
    public int selectedPiece() { return data.get(D_PIECE); }
    public int imbueProgress() { return data.get(D_IMBUE_PROGRESS); }
    public boolean imbuing() { return imbueProgress() > 0; }
    public float imbueFraction() {
        return Math.min(1f, imbueProgress() / (float) ArmorFabricatorBlockEntity.IMBUE_TICKS);
    }
    public float energyFraction() { return Math.min(1f, energy() / (float) energyCapacity()); }
    public float tankFraction() { return Math.min(1f, tankAmount() / (float) tankCapacity()); }
    public float progressFraction() { return maxProgress() <= 0 ? 0f : Math.min(1f, progress() / (float) maxProgress()); }

    public boolean tolerates(KiEnergyType type) {
        int slot = com.hmc.zenkai.content.blockentity.KiContainmentChamberBlockEntity.toleranceSlot(type);
        return slot < 0 || this.slots.get(slot).hasItem();
    }

    public ItemStack machineStack(int slot) { return this.slots.get(slot).getItem(); }

    // ── Red ──────────────────────────────────────────────────────────────────
    @Override
    public boolean clickMenuButton(@NotNull Player player, int id) {
        if (id == BTN_TAB_CRAFT || id == BTN_TAB_ATTR) {
            setTab(id == BTN_TAB_ATTR ? TAB_ATTR : TAB_CRAFT);
            if (be != null) be.setLastTab(tab);   // se recuerda en la máquina para la próxima apertura
            return true;
        }
        if (be == null || !(player instanceof ServerPlayer sp)) return false;
        if (id == BTN_TOGGLE_CRAFT) {
            be.toggleCraft();
            return true;
        }
        if (id >= BTN_PIECE_FIRST && id < BTN_PIECE_FIRST + ArmorFabricatorBlockEntity.PIECES.length) {
            be.selectPiece(id - BTN_PIECE_FIRST);
            return true;
        }
        if (id == BTN_IMBUE) {
            ArmorFabricatorBlockEntity.ImbueResult r = be.startImbue();
            if (r == ArmorFabricatorBlockEntity.ImbueResult.BAD_TEMPLATE && be.templateProblemKey() != null) {
                sp.displayClientMessage(Component.translatable(be.templateProblemKey()), true);
            } else if (r != ArmorFabricatorBlockEntity.ImbueResult.OK) {
                sp.displayClientMessage(Component.translatable(r.langKey()), true);
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        return stillValid(access, player, ModBlocks.ARMOR_FABRICATOR.get());
    }

    /**
     * Shift-clic. De la máquina al inventario siempre. Del inventario: mejoras a su ranura; patrón y material en la
     * pestaña Fabricar; pieza y sangre en Atributos (una pieza de armadura en Fabricar no se mueve: no es material).
     */
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
            for (int i = 0; i < ArmorFabricatorBlockEntity.UPGRADE_SLOTS && !moved; i++) {
                if (ArmorFabricatorBlockEntity.validFor(i, stack)) moved = moveItemStackTo(stack, i, i + 1, false);
            }
            if (!moved && tab == TAB_ATTR) {
                if (stack.getItem() instanceof BloodItem) {
                    moved = moveItemStackTo(stack, ArmorFabricatorBlockEntity.SLOT_BLOOD, ArmorFabricatorBlockEntity.SLOT_BLOOD + 1, false);
                } else if (AttributeTemplates.kindOf(stack) != null) {
                    moved = moveItemStackTo(stack, ArmorFabricatorBlockEntity.SLOT_TEMPLATE, ArmorFabricatorBlockEntity.SLOT_TEMPLATE + 1, false);
                } else if (ArmorFabricatorBlockEntity.isArmorPiece(stack)) {
                    // Primero la pieza; si ya hay una, al destino (copiar/transferir).
                    moved = moveItemStackTo(stack, ArmorFabricatorBlockEntity.SLOT_ARMOR, ArmorFabricatorBlockEntity.SLOT_ARMOR + 1, false)
                            || moveItemStackTo(stack, ArmorFabricatorBlockEntity.SLOT_TARGET, ArmorFabricatorBlockEntity.SLOT_TARGET + 1, false);
                }
            } else if (!moved && tab == TAB_CRAFT && !ArmorFabricatorBlockEntity.isArmorPiece(stack)) {
                if (ArmorSets.setOf(stack) != null || AttributeTemplates.kindOf(stack) != null) {
                    moved = moveItemStackTo(stack, ArmorFabricatorBlockEntity.SLOT_PATTERN, ArmorFabricatorBlockEntity.SLOT_PATTERN + 1, false);
                } else {
                    int first = ArmorFabricatorBlockEntity.SLOT_MATERIAL_FIRST;
                    moved = moveItemStackTo(stack, first, first + ArmorFabricatorBlockEntity.MATERIAL_SLOTS, false);
                }
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
