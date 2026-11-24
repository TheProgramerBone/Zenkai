package com.hmc.zenkai.client.gui.menu;

import com.hmc.zenkai.client.gui.ModMenuTypes;
import com.hmc.zenkai.content.blockentity.KiInfuserBlockEntity;
import com.hmc.zenkai.content.blockentity.KiMachineBlockEntity;
import com.hmc.zenkai.feature.armor.KiInfusionRecipe;
import com.hmc.zenkai.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Menú de la infusora de Ki (Fase 5c). Ranuras en this.slots en el orden de KiInfuserBlockEntity:
 *   0..3 mejoras · 4 entrada · 5 salida · 6..41 inventario y hotbar (una sola tolerancia desde el 2026-11-23).
 * LAS COORDENADAS SON PARTE DEL ASSET: cada ranura tiene su pozo en ki_infuser.png (tools/gen_ki_infuser_screen.py).
 * Lo común (mejoras, inventario, FE, tanque, shift-clic) vive en KiMachineMenu desde el 2026-11-23.
 */
public class KiInfuserMenu extends KiMachineMenu {

    public static final int D_PROGRESS = KiMachineBlockEntity.SHARED_DATA, D_WORKING = D_PROGRESS + 1,
            D_MAX_PROGRESS = D_PROGRESS + 2;
    public static final int DATA_SLOTS = D_PROGRESS + 3;

    public static final int MACHINE_SLOTS = KiInfuserBlockEntity.SLOTS;

    // ── Layout (== tools/gen_ki_infuser_screen.py) ───────────────────────────
    public static final int IN_X = 92, IN_Y = 52, OUT_X = 156, OUT_Y = 52;

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
        super(ModMenuTypes.KI_INFUSER.get(), id, data, access, pos, MACHINE_SLOTS,
                KiInfuserBlockEntity::validFor, KiInfuserBlockEntity::slotLimit);
        addUpgradeSlots(machine);
        addMachineSlot(machine, KiInfuserBlockEntity.SLOT_INPUT, IN_X, IN_Y, -1);
        addMachineSlot(machine, KiInfuserBlockEntity.SLOT_OUTPUT, OUT_X, OUT_Y, -1);
        addPlayerInventory(inv);
        addDataSlots(data);
        this.level = inv.player.level();
    }

    /** Para las recetas en el shift-clic (en el cliente, la copia sincronizada del recipe manager). */
    private final Level level;

    // ── Lectura ──────────────────────────────────────────────────────────────
    public int progress() { return data.get(D_PROGRESS); }
    public boolean isWorking() { return data.get(D_WORKING) != 0; }
    public float progressFraction() {
        int max = data.get(D_MAX_PROGRESS);
        return max <= 0 ? 0f : Math.min(1f, progress() / (float) max);
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        return stillValid(access, player, ModBlocks.KI_INFUSER.get());
    }

    /** Shift-clic del inventario: lo que tenga receta de infusión, a la entrada (las mejoras ya las coloca la base). */
    @Override
    protected boolean moveIntoMachine(ItemStack stack) {
        return KiInfusionRecipe.isInput(level.getRecipeManager(), stack)
                && moveItemStackTo(stack, KiInfuserBlockEntity.SLOT_INPUT, KiInfuserBlockEntity.SLOT_INPUT + 1, false);
    }
}
