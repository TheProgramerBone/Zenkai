package com.hmc.zenkai.client.gui.menu;

import com.hmc.zenkai.client.gui.ModMenuTypes;
import com.hmc.zenkai.content.blockentity.ScouterBenchBlockEntity;
import com.hmc.zenkai.content.item.ScouterItem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Menú del banco de scouter: un slot para el aparato + el inventario del jugador (de donde
 * salen los materiales) + los datos del trabajo en curso.
 *
 * Los botones NO llevan paquete propio: van por clickMenuButton, que es red vanilla del menú
 * y ya trae la validación de "este jugador tiene este menú abierto". Un payload nuestro solo
 * añadiría otro sitio donde revalidar un BlockPos a mano.
 *  id 0..N-1 = mejora por ordinal de ScouterUpgrade
 *  id 99     = reparar
 *  id 100    = cancelar
 */
public class ScouterBenchMenu extends AbstractContainerMenu {

    public static final int BTN_REPAIR = ScouterBenchBlockEntity.JOB_REPAIR;
    public static final int BTN_CANCEL = 100;

    private final Container container;
    private final ContainerData data;

    /** Cliente: contenedor y datos ficticios; el servidor los rellena por sincronización. */
    public ScouterBenchMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(id, inv, new SimpleContainer(1), new SimpleContainerData(4));
    }

    public ScouterBenchMenu(int id, Inventory inv, Container container, ContainerData data) {
        super(ModMenuTypes.SCOUTER_BENCH.get(), id);
        checkContainerSize(container, 1);
        this.container = container;
        this.data = data;

        addSlot(new Slot(container, 0, 20, 40) {
            @Override public boolean mayPlace(@NotNull ItemStack stack) {
                return stack.getItem() instanceof ScouterItem;
            }
            @Override public int getMaxStackSize() { return 1; }
        });

        addPlayerInventory(inv);
        addDataSlots(data);
    }

    private void addPlayerInventory(Inventory inv) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inv, col + row * 9 + 9, 48 + col * 18, 166 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inv, col, 48 + col * 18, 224));
        }
    }

    public ItemStack scouter()  { return container.getItem(0); }
    public int progress()       { return data.get(0); }
    public int duration()       { return Math.max(1, data.get(1)); }
    public int job()            { return data.get(2); }
    public boolean isPaused()   { return data.get(3) != 0; }

    public boolean isWorking()  { return job() != ScouterBenchBlockEntity.JOB_NONE; }

    /** 0..1 para la barra. */
    public float progressFraction() {
        return isWorking() ? Math.min(1f, progress() / (float) duration()) : 0f;
    }

    @Override
    public boolean clickMenuButton(@NotNull Player player, int id) {
        if (!(player instanceof ServerPlayer sp)) return false;
        if (!(container instanceof ScouterBenchBlockEntity be)) return false;

        if (id == BTN_CANCEL) {
            be.cancelJob();
            return true;
        }
        return be.startJob(sp, id);
    }

    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;

        ItemStack raw = slot.getItem();
        ItemStack copy = raw.copy();

        if (index == 0) {
            // Del banco al jugador.
            if (!moveItemStackTo(raw, 1, slots.size(), true)) return ItemStack.EMPTY;
        } else if (raw.getItem() instanceof ScouterItem) {
            // Shift+click de un scouter: entra al banco.
            if (!moveItemStackTo(raw, 0, 1, false)) return ItemStack.EMPTY;
        } else {
            return ItemStack.EMPTY; // los materiales se quedan en el inventario, no se meten
        }

        if (raw.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        return copy;
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        return container.stillValid(player);
    }
}