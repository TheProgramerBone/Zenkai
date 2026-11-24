package com.hmc.zenkai.client.gui.menu;

import com.hmc.zenkai.client.gui.ModMenuTypes;
import com.hmc.zenkai.content.blockentity.CrusherBlockEntity;
import com.hmc.zenkai.content.blockentity.KiMachineBlockEntity;
import com.hmc.zenkai.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Menú del Triturador (Fase 5d). Ranuras en this.slots en el orden de CrusherBlockEntity:
 *   0..3 mejoras · 4 entrada · 5..8 salidas (2x2) · 9..44 inventario y hotbar.
 * LAS COORDENADAS SON PARTE DEL ASSET: cada ranura tiene su pozo en crusher.png (tools/gen_crusher_screen.py).
 */
public class CrusherMenu extends KiMachineMenu {

    public static final int D_PROGRESS = KiMachineBlockEntity.SHARED_DATA, D_MAX_PROGRESS = D_PROGRESS + 1, D_FLAGS = D_PROGRESS + 2;
    public static final int DATA_SLOTS = D_PROGRESS + 3;
    public static final int F_WORKING = 1, F_TURBO = 2;

    public static final int MACHINE_SLOTS = CrusherBlockEntity.SLOTS;

    // ── Layout (== tools/gen_crusher_screen.py) ──────────────────────────────
    public static final int IN_X = 84, IN_Y = 52, OUT_X = 148, OUT_Y = 43;

    public CrusherMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(id, inv, new ItemStackHandler(MACHINE_SLOTS), new SimpleContainerData(DATA_SLOTS),
                ContainerLevelAccess.NULL, buf.readBlockPos());
    }

    public CrusherMenu(int id, Inventory inv, CrusherBlockEntity be) {
        this(id, inv, be.items(), be.containerData(),
                ContainerLevelAccess.create(Objects.requireNonNull(be.getLevel()), be.getBlockPos()), be.getBlockPos());
    }

    private CrusherMenu(int id, Inventory inv, IItemHandler machine, ContainerData data, ContainerLevelAccess access,
                        BlockPos pos) {
        super(ModMenuTypes.CRUSHER.get(), id, data, access, pos, MACHINE_SLOTS,
                CrusherBlockEntity::validFor, CrusherBlockEntity::slotLimit);
        addUpgradeSlots(machine);
        addMachineSlot(machine, CrusherBlockEntity.SLOT_INPUT, IN_X, IN_Y, -1);
        for (int i = 0; i < CrusherBlockEntity.OUTPUT_SLOTS; i++) {
            addMachineSlot(machine, CrusherBlockEntity.SLOT_OUTPUT_FIRST + i, OUT_X + (i % 2) * STEP, OUT_Y + (i / 2) * STEP, -1);
        }
        addPlayerInventory(inv);
        addDataSlots(data);
    }

    // ── Lectura ──────────────────────────────────────────────────────────────
    public int progress() { return data.get(D_PROGRESS); }
    public int maxProgress() { return data.get(D_MAX_PROGRESS); }
    public boolean isWorking() { return (data.get(D_FLAGS) & F_WORKING) != 0; }
    public boolean isTurbo() { return (data.get(D_FLAGS) & F_TURBO) != 0; }
    public float progressFraction() { return maxProgress() <= 0 ? 0f : Math.min(1f, progress() / (float) maxProgress()); }

    @Override
    public boolean stillValid(@NotNull Player player) {
        return stillValid(access, player, ModBlocks.CRUSHER.get());
    }

    /** Shift-clic del inventario: a la entrada (la receta decide si sirve). */
    @Override
    protected boolean moveIntoMachine(ItemStack stack) {
        return moveItemStackTo(stack, CrusherBlockEntity.SLOT_INPUT, CrusherBlockEntity.SLOT_INPUT + 1, false);
    }
}
