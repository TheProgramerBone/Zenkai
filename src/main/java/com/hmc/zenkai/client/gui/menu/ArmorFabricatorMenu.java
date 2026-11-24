package com.hmc.zenkai.client.gui.menu;

import com.hmc.zenkai.client.gui.ModMenuTypes;
import com.hmc.zenkai.content.blockentity.ArmorFabricatorBlockEntity;
import com.hmc.zenkai.content.blockentity.KiMachineBlockEntity;
import com.hmc.zenkai.feature.armor.ArmorSets;
import com.hmc.zenkai.feature.armor.AttributeTemplates;
import com.hmc.zenkai.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Menú de la Fabricadora (Fase 5b; solo la pestaña Fabricar desde que el 2026-11-23 Atributos e Infundir pasaron a la
 * estación de imbuido, ImbuingStationMenu). Ranuras en this.slots, en el orden de ArmorFabricatorBlockEntity:
 *   0..3 mejoras · 4 patrón · 5..13 material · 14 salida · 15..50 inventario y hotbar.
 * LAS COORDENADAS SON PARTE DEL ASSET: cada ranura tiene su pozo en armor_fabricator_craft.png
 * (tools/gen_armor_fabricator_screen.py). Cifras grandes partidas en dos huecos, como KiChamberMenu.
 */
public class ArmorFabricatorMenu extends KiMachineMenu {

    public static final int D_PROGRESS = KiMachineBlockEntity.SHARED_DATA, D_MAX_PROGRESS = D_PROGRESS + 1;
    public static final int D_FLAGS = D_PROGRESS + 2, D_PIECE = D_PROGRESS + 3;
    public static final int DATA_SLOTS = D_PROGRESS + 4;
    public static final int F_WORKING = 1, F_TURBO = 2, F_ENABLED = 4;

    /** Botones: 0..3 pieza, 7 interruptor de la fabricación (mismos números que antes de separar). */
    public static final int BTN_PIECE_FIRST = 0, BTN_TOGGLE_CRAFT = 7;

    public static final int MACHINE_SLOTS = ArmorFabricatorBlockEntity.SLOTS;

    // ── Layout (== tools/gen_armor_fabricator_screen.py) ─────────────────────
    public static final int PATTERN_X = 66, PATTERN_Y = 34;
    public static final int MAT_X = 66, MAT_Y = 58;
    public static final int OUT_X = 166, OUT_Y = 76;

    @Nullable private final ArmorFabricatorBlockEntity be;

    public ArmorFabricatorMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(id, inv, null, new ItemStackHandler(MACHINE_SLOTS), new SimpleContainerData(DATA_SLOTS),
                ContainerLevelAccess.NULL, buf.readBlockPos());
    }

    public ArmorFabricatorMenu(int id, Inventory inv, ArmorFabricatorBlockEntity be) {
        this(id, inv, be, be.items(), be.containerData(),
                ContainerLevelAccess.create(Objects.requireNonNull(be.getLevel()), be.getBlockPos()), be.getBlockPos());
    }

    private ArmorFabricatorMenu(int id, Inventory inv, @Nullable ArmorFabricatorBlockEntity be, IItemHandler machine,
                                ContainerData data, ContainerLevelAccess access, BlockPos pos) {
        super(ModMenuTypes.ARMOR_FABRICATOR.get(), id, data, access, pos, MACHINE_SLOTS,
                ArmorFabricatorBlockEntity::validFor, ArmorFabricatorBlockEntity::slotLimit);
        this.be = be;
        addUpgradeSlots(machine);
        addMachineSlot(machine, ArmorFabricatorBlockEntity.SLOT_PATTERN, PATTERN_X, PATTERN_Y, -1);
        for (int i = 0; i < ArmorFabricatorBlockEntity.MATERIAL_SLOTS; i++) {
            addMachineSlot(machine, ArmorFabricatorBlockEntity.SLOT_MATERIAL_FIRST + i,
                    MAT_X + (i % 3) * STEP, MAT_Y + (i / 3) * STEP, -1);
        }
        addMachineSlot(machine, ArmorFabricatorBlockEntity.SLOT_OUTPUT, OUT_X, OUT_Y, -1);
        addPlayerInventory(inv);
        addDataSlots(data);
    }

    // ── Lectura ──────────────────────────────────────────────────────────────
    public int progress() { return data.get(D_PROGRESS); }
    public int maxProgress() { return data.get(D_MAX_PROGRESS); }
    public boolean isWorking() { return (data.get(D_FLAGS) & F_WORKING) != 0; }
    public boolean isTurbo() { return (data.get(D_FLAGS) & F_TURBO) != 0; }
    public boolean craftEnabled() { return (data.get(D_FLAGS) & F_ENABLED) != 0; }
    public int selectedPiece() { return data.get(D_PIECE); }
    public float progressFraction() { return maxProgress() <= 0 ? 0f : Math.min(1f, progress() / (float) maxProgress()); }

    // ── Red ──────────────────────────────────────────────────────────────────
    @Override
    public boolean clickMenuButton(@NotNull Player player, int id) {
        if (be == null || !(player instanceof ServerPlayer)) return false;
        if (id == BTN_TOGGLE_CRAFT) {
            be.toggleCraft();
            return true;
        }
        if (id >= BTN_PIECE_FIRST && id < BTN_PIECE_FIRST + ArmorFabricatorBlockEntity.PIECES.length) {
            be.selectPiece(id - BTN_PIECE_FIRST);
            return true;
        }
        return false;
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        return stillValid(access, player, ModBlocks.ARMOR_FABRICATOR.get());
    }

    /** Shift-clic del inventario: mejoras a su ranura; patrón o plantilla al patrón; el resto al material. */
    @Override
    protected boolean moveIntoMachine(ItemStack stack) {
        if (ArmorSets.setOf(stack) != null || AttributeTemplates.kindOf(stack) != null) {
            return moveItemStackTo(stack, ArmorFabricatorBlockEntity.SLOT_PATTERN, ArmorFabricatorBlockEntity.SLOT_PATTERN + 1, false);
        }
        int first = ArmorFabricatorBlockEntity.SLOT_MATERIAL_FIRST;
        return moveItemStackTo(stack, first, first + ArmorFabricatorBlockEntity.MATERIAL_SLOTS, false);
    }

    /** Lo de la ranura del patrón (para la pantalla y JEI). */
    public ItemStack pattern() { return machineStack(ArmorFabricatorBlockEntity.SLOT_PATTERN); }
}
