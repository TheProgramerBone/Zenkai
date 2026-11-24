package com.hmc.zenkai.client.gui.menu;

import com.hmc.zenkai.client.gui.ModMenuTypes;
import com.hmc.zenkai.content.blockentity.ImbuingStationBlockEntity;
import com.hmc.zenkai.content.blockentity.KiMachineBlockEntity;
import com.hmc.zenkai.content.item.BloodItem;
import com.hmc.zenkai.feature.armor.AttributeTemplates;
import com.hmc.zenkai.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
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
 * Menú de la Estación de imbuido (2026-11-23: las pestañas Atributos e Infundir de la antigua máquina de fabricación).
 * Ranuras en this.slots, en el orden de ImbuingStationBlockEntity:
 *   0..3 mejoras (siempre visibles) · 4 pieza · 5 sangre · 6 plantilla · 7 pieza destino (pestaña ATRIBUTOS)
 *   8 pieza · 9 lingotes infundidos (pestaña INFUNDIR) · 10..45 inventario y hotbar.
 * LAS COORDENADAS SON PARTE DEL ASSET: cada ranura tiene su pozo en armor_fabricator_attributes/_infuse.png
 * (tools/gen_armor_fabricator_screen.py; la estación reutiliza esos dos fondos tal cual).
 */
public class ImbuingStationMenu extends KiMachineMenu {

    public static final int D_IMBUE_PROGRESS = KiMachineBlockEntity.SHARED_DATA, D_INFUSE_PROGRESS = D_IMBUE_PROGRESS + 1;
    public static final int DATA_SLOTS = D_IMBUE_PROGRESS + 2;

    /** Botones: imbuir, infundir y las dos pestañas. */
    public static final int BTN_IMBUE = 0, BTN_INFUSE = 1, BTN_TAB_ATTR = 2, BTN_TAB_INFUSE = 3;
    public static final int TAB_ATTR = 0, TAB_INFUSE = 1;

    public static final int MACHINE_SLOTS = ImbuingStationBlockEntity.SLOTS;

    // ── Layout (== tools/gen_armor_fabricator_screen.py) ─────────────────────
    public static final int ARMOR_X = 66, ARMOR_Y = 36, BLOOD_X = 66, BLOOD_Y = 64;
    /** Arriba pieza → destino (copiar/transferir); abajo sangre y plantilla. */
    public static final int TARGET_X = 96, TARGET_Y = 36, TEMPLATE_X = 96, TEMPLATE_Y = 64;
    /** Pestaña Infundir: los mismos pozos que la pieza y la sangre de Atributos (un PNG casi gemelo). */
    public static final int INFUSE_PIECE_X = 66, INFUSE_PIECE_Y = 36, INFUSE_INGOTS_X = 66, INFUSE_INGOTS_Y = 64;

    @Nullable private final ImbuingStationBlockEntity be;

    public ImbuingStationMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(id, inv, null, new ItemStackHandler(MACHINE_SLOTS), new SimpleContainerData(DATA_SLOTS),
                ContainerLevelAccess.NULL, buf.readBlockPos());
        setTab(buf.readVarInt());   // la pestaña guardada en la máquina (ImbuingStationBlock la escribe tras la posición)
    }

    public ImbuingStationMenu(int id, Inventory inv, ImbuingStationBlockEntity be) {
        this(id, inv, be, be.items(), be.containerData(),
                ContainerLevelAccess.create(Objects.requireNonNull(be.getLevel()), be.getBlockPos()), be.getBlockPos());
        setTab(be.lastTab());
    }

    private ImbuingStationMenu(int id, Inventory inv, @Nullable ImbuingStationBlockEntity be, IItemHandler machine,
                               ContainerData data, ContainerLevelAccess access, BlockPos pos) {
        super(ModMenuTypes.IMBUING_STATION.get(), id, data, access, pos, MACHINE_SLOTS,
                ImbuingStationBlockEntity::validFor, ImbuingStationBlockEntity::slotLimit);
        this.be = be;
        addUpgradeSlots(machine);
        addMachineSlot(machine, ImbuingStationBlockEntity.SLOT_ARMOR, ARMOR_X, ARMOR_Y, TAB_ATTR);
        addMachineSlot(machine, ImbuingStationBlockEntity.SLOT_BLOOD, BLOOD_X, BLOOD_Y, TAB_ATTR);
        addMachineSlot(machine, ImbuingStationBlockEntity.SLOT_TEMPLATE, TEMPLATE_X, TEMPLATE_Y, TAB_ATTR);
        addMachineSlot(machine, ImbuingStationBlockEntity.SLOT_TARGET, TARGET_X, TARGET_Y, TAB_ATTR);
        addMachineSlot(machine, ImbuingStationBlockEntity.SLOT_INFUSE_PIECE, INFUSE_PIECE_X, INFUSE_PIECE_Y, TAB_INFUSE);
        addMachineSlot(machine, ImbuingStationBlockEntity.SLOT_INFUSE_INGOTS, INFUSE_INGOTS_X, INFUSE_INGOTS_Y, TAB_INFUSE);
        addPlayerInventory(inv);
        addDataSlots(data);
    }

    public void setTab(int t) { tab = t == TAB_INFUSE ? TAB_INFUSE : TAB_ATTR; }

    // ── Lectura ──────────────────────────────────────────────────────────────
    public int imbueProgress() { return data.get(D_IMBUE_PROGRESS); }
    public boolean imbuing() { return imbueProgress() > 0; }
    public int infuseProgress() { return data.get(D_INFUSE_PROGRESS); }
    public boolean infusing() { return infuseProgress() > 0; }
    public boolean isWorking() { return imbuing() || infusing(); }
    public float imbueFraction() {
        return Math.min(1f, imbueProgress() / (float) ImbuingStationBlockEntity.IMBUE_TICKS);
    }
    public float infuseFraction() {
        return Math.min(1f, infuseProgress() / (float) ImbuingStationBlockEntity.INFUSE_TICKS);
    }

    // ── Red ──────────────────────────────────────────────────────────────────
    @Override
    public boolean clickMenuButton(@NotNull Player player, int id) {
        if (id == BTN_TAB_ATTR || id == BTN_TAB_INFUSE) {
            setTab(id == BTN_TAB_INFUSE ? TAB_INFUSE : TAB_ATTR);
            if (be != null) be.setLastTab(tab);   // se recuerda en la máquina para la próxima apertura
            return true;
        }
        if (be == null || !(player instanceof ServerPlayer sp)) return false;
        if (id == BTN_INFUSE) {
            ImbuingStationBlockEntity.ImbueResult r = be.startInfuse();
            if (r == ImbuingStationBlockEntity.ImbueResult.NOT_HIGHER || r == ImbuingStationBlockEntity.ImbueResult.NO_INGOTS) {
                String k = be.infuseProblemKey();
                sp.displayClientMessage(Component.translatable(k != null ? k : r.langKey()), true);
            } else if (r != ImbuingStationBlockEntity.ImbueResult.OK) {
                sp.displayClientMessage(Component.translatable(r.langKey()), true);
            }
            return true;
        }
        if (id == BTN_IMBUE) {
            ImbuingStationBlockEntity.ImbueResult r = be.startImbue();
            if (r == ImbuingStationBlockEntity.ImbueResult.BAD_TEMPLATE && be.templateProblemKey() != null) {
                sp.displayClientMessage(Component.translatable(be.templateProblemKey()), true);
            } else if (r != ImbuingStationBlockEntity.ImbueResult.OK) {
                sp.displayClientMessage(Component.translatable(r.langKey()), true);
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        return stillValid(access, player, ModBlocks.IMBUING_STATION.get());
    }

    /** Shift-clic: pieza y sangre/plantilla en Atributos; pieza y lingotes infundidos en Infundir. */
    @Override
    protected boolean moveIntoMachine(ItemStack stack) {
        if (tab == TAB_ATTR) {
            if (stack.getItem() instanceof BloodItem) {
                return moveItemStackTo(stack, ImbuingStationBlockEntity.SLOT_BLOOD, ImbuingStationBlockEntity.SLOT_BLOOD + 1, false);
            }
            if (AttributeTemplates.kindOf(stack) != null) {
                return moveItemStackTo(stack, ImbuingStationBlockEntity.SLOT_TEMPLATE, ImbuingStationBlockEntity.SLOT_TEMPLATE + 1, false);
            }
            if (ImbuingStationBlockEntity.isArmorPiece(stack)) {
                // Primero la pieza; si ya hay una, al destino (copiar/transferir).
                return moveItemStackTo(stack, ImbuingStationBlockEntity.SLOT_ARMOR, ImbuingStationBlockEntity.SLOT_ARMOR + 1, false)
                        || moveItemStackTo(stack, ImbuingStationBlockEntity.SLOT_TARGET, ImbuingStationBlockEntity.SLOT_TARGET + 1, false);
            }
            return false;
        }
        int target = ImbuingStationBlockEntity.isArmorPiece(stack) ? ImbuingStationBlockEntity.SLOT_INFUSE_PIECE
                : ImbuingStationBlockEntity.SLOT_INFUSE_INGOTS;
        return ImbuingStationBlockEntity.validFor(target, stack) && moveItemStackTo(stack, target, target + 1, false);
    }
}
