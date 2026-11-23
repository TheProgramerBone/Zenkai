package com.hmc.zenkai.client.gui.menu;

import com.hmc.zenkai.client.gui.ModMenuTypes;
import com.hmc.zenkai.content.blockentity.GravityChamberBlockEntity;
import com.hmc.zenkai.content.item.GravityUpgradeItem;
import com.hmc.zenkai.content.item.TrainingDroneItem;
import com.hmc.zenkai.feature.gravity.GravityDroneRound;
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
 * Menú de la consola de la cámara de gravedad. Ranura 0 = la mejora de gravedad; 1..8 = drones (Fase 4);
 * 9..44 = inventario. Las ranuras de drones solo están ACTIVAS (se ven y se pueden pulsar) en la vista
 * «Drones» de la pantalla ({@link #setDroneView}); en el servidor siempre lo están.
 * LA COORDENADA DE LA RANURA ES PARTE DEL ASSET: su pozo está pintado en gravity_chamber.png
 * (tools/gen_gravity_chamber_screen.py) en (SLOT_X-1, SLOT_Y-1), 18x18.
 * Los FE viajan partidos en dos mitades de 16 bits (ContainerData manda shorts, ver config-y-red.md).
 * Botones por clickMenuButton (red vanilla del menú, ya valida que el jugador tenga ESTE menú abierto).
 */
public class GravityChamberMenu extends AbstractContainerMenu {

    public static final int D_FE_LO = 0, D_FE_HI = 1, D_CAP_LO = 2, D_CAP_HI = 3;
    public static final int D_TARGET_G = 4, D_MAX_G = 5, D_STATUS = 6, D_INTERIOR = 7, D_LEAKS = 8;
    public static final int D_RADIUS = 9, D_HOLOGRAM = 10, D_COST = 11, D_PORTS = 12, D_ENABLED = 13;
    public static final int D_MAX_DRONES = 14, D_DRONE_MODE = 15, D_ROUND_RUNNING = 16, D_ROUND_LEFT = 17;
    public static final int D_ROUND_DONE = 18, D_ROUND_TOTAL = 19, D_LAST_RESULT = 20, D_LAST_TP_LO = 21, D_LAST_TP_HI = 22;
    public static final int D_ROUND_AGGRESSIVE = 23, D_DIFFICULTY = 24;
    public static final int DATA_SLOTS = 25;

    public static final int BTN_G_MINUS10 = 0, BTN_G_MINUS1 = 1, BTN_G_PLUS1 = 2, BTN_G_PLUS10 = 3;
    public static final int BTN_RADIUS_MINUS = 4, BTN_RADIUS_PLUS = 5, BTN_HOLOGRAM = 6, BTN_REVALIDATE = 7;
    public static final int BTN_POWER = 8, BTN_DRONE_MODE = 9, BTN_ROUND = 10, BTN_DIFFICULTY = 11;
    /** Cambio de vista: solo para que la consola la recuerde (las ranuras del servidor están siempre activas). */
    public static final int BTN_VIEW_GRAVITY = 12, BTN_VIEW_DRONES = 13;

    /** == SLOT_X/SLOT_Y de gen_gravity_chamber_screen.py. */
    public static final int SLOT_X = 100, SLOT_Y = 117;
    private static final int INV_X = 47, INV_Y = 170, HOTBAR_Y = 229;
    /** Rejilla 4x2 de las ranuras de drones (esquina del primer pozo + 1), dentro del panel de lectura. */
    public static final int DRONE_X = 146, DRONE_Y = 43;
    public static final int DRONE_FIRST = 1, DRONE_END = 1 + GravityDroneRound.MAX_DRONES;
    public static final int INV_FIRST = DRONE_END, INV_END = DRONE_END + 36;
    /** Vista de la pantalla (solo cliente; en el servidor las ranuras siempre están activas). */
    private boolean droneView;

    private final ContainerData data;
    private final ContainerLevelAccess access;
    private final BlockPos pos;
    @Nullable private final GravityChamberBlockEntity be;

    public GravityChamberMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(id, inv, null, new ItemStackHandler(1), new ItemStackHandler(GravityDroneRound.MAX_DRONES),
                new SimpleContainerData(DATA_SLOTS), ContainerLevelAccess.NULL, buf.readBlockPos());
        this.droneView = buf.readBoolean();   // vista guardada en la consola (GravityChamberBlock la escribe tras la posición)
    }

    public GravityChamberMenu(int id, Inventory inv, GravityChamberBlockEntity be) {
        this(id, inv, be, be.upgradeHandler(), be.droneHandler(), be.containerData(),
                ContainerLevelAccess.create(Objects.requireNonNull(be.getLevel()), be.getBlockPos()), be.getBlockPos());
        this.droneView = true;
    }

    private GravityChamberMenu(int id, Inventory inv, @Nullable GravityChamberBlockEntity be, IItemHandler machine,
                               IItemHandler droneSlots, ContainerData data, ContainerLevelAccess access, BlockPos pos) {
        super(ModMenuTypes.GRAVITY_CHAMBER.get(), id);
        this.be = be;
        this.data = data;
        this.access = access;
        this.pos = pos;

        // Filtro en el Slot además de en el handler: en cliente el handler es uno vacío sin reglas.
        addSlot(new SlotItemHandler(machine, 0, SLOT_X, SLOT_Y) {
            @Override public boolean mayPlace(@NotNull ItemStack stack) { return stack.getItem() instanceof GravityUpgradeItem; }
            @Override public int getMaxStackSize() { return 1; }
            @Override public int getMaxStackSize(@NotNull ItemStack stack) { return 1; }
        });
        // Drones: filtro también en el Slot (en cliente el handler es uno vacío) y bloqueo durante la ronda.
        for (int i = 0; i < GravityDroneRound.MAX_DRONES; i++) {
            addSlot(new SlotItemHandler(droneSlots, i, DRONE_X + (i % 4) * 18, DRONE_Y + (i / 4) * 18) {
                @Override public boolean mayPlace(@NotNull ItemStack stack) {
                    return stack.getItem() instanceof TrainingDroneItem && !roundRunning();
                }
                @Override public boolean mayPickup(@NotNull Player player) { return !roundRunning() && super.mayPickup(player); }
                @Override public int getMaxStackSize() { return 1; }
                @Override public int getMaxStackSize(@NotNull ItemStack stack) { return 1; }
                @Override public boolean isActive() { return droneView; }
            });
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inv, col + row * 9 + 9, INV_X + col * 18, INV_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) addSlot(new Slot(inv, col, INV_X + col * 18, HOTBAR_Y));
        addDataSlots(data);
    }

    public BlockPos pos() { return pos; }

    private int wide(int lo, int hi) { return ((data.get(hi) & 0xFFFF) << 16) | (data.get(lo) & 0xFFFF); }

    public int energy() { return wide(D_FE_LO, D_FE_HI); }
    public int energyCapacity() { return Math.max(1, wide(D_CAP_LO, D_CAP_HI)); }
    public float energyFraction() { return Math.min(1f, energy() / (float) energyCapacity()); }
    public int targetGravity() { return data.get(D_TARGET_G); }
    public int maxGravity() { return data.get(D_MAX_G); }
    public int status() { return data.get(D_STATUS); }
    public int interior() { return data.get(D_INTERIOR); }
    public int leaks() { return data.get(D_LEAKS); }
    public int radius() { return data.get(D_RADIUS); }
    public boolean hologram() { return data.get(D_HOLOGRAM) != 0; }
    public int cost() { return data.get(D_COST); }
    public int ports() { return data.get(D_PORTS); }
    public boolean enabled() { return data.get(D_ENABLED) != 0; }
    public int maxDrones() { return data.get(D_MAX_DRONES); }
    public boolean aggressiveMode() { return data.get(D_DRONE_MODE) != 0; }
    public boolean roundRunning() { return data.get(D_ROUND_RUNNING) != 0; }
    public boolean roundAggressive() { return data.get(D_ROUND_AGGRESSIVE) != 0; }
    public int roundSecondsLeft() { return data.get(D_ROUND_LEFT); }
    public int roundDone() { return data.get(D_ROUND_DONE); }
    public int roundTotal() { return data.get(D_ROUND_TOTAL); }
    public int lastResult() { return data.get(D_LAST_RESULT); }
    public int lastTp() { return wide(D_LAST_TP_LO, D_LAST_TP_HI); }
    public com.hmc.zenkai.feature.gravity.DroneDifficulty difficulty() {
        return com.hmc.zenkai.feature.gravity.DroneDifficulty.byId(data.get(D_DIFFICULTY));
    }
    public boolean droneView() { return droneView; }
    public void setDroneView(boolean v) { droneView = v; }

    /** Drones con usos en las ranuras (lectura del cliente: las ranuras se sincronizan solas). */
    public int usableDrones() {
        int n = 0;
        for (int i = DRONE_FIRST; i < DRONE_END; i++) {
            ItemStack s = slots.get(i).getItem();
            if (s.getItem() instanceof TrainingDroneItem && TrainingDroneItem.usesLeft(s) > 0) n++;
        }
        return n;
    }

    @Override
    public boolean clickMenuButton(@NotNull Player player, int id) {
        if (be == null) return false;
        switch (id) {
            case BTN_G_MINUS10 -> be.addTargetGravity(-10);
            case BTN_G_MINUS1 -> be.addTargetGravity(-1);
            case BTN_G_PLUS1 -> be.addTargetGravity(1);
            case BTN_G_PLUS10 -> be.addTargetGravity(10);
            case BTN_RADIUS_MINUS -> be.addRadius(-1);
            case BTN_RADIUS_PLUS -> be.addRadius(1);
            case BTN_HOLOGRAM -> be.toggleHologram();
            case BTN_REVALIDATE -> be.revalidateNow();
            case BTN_POWER -> be.toggleEnabled();
            case BTN_DRONE_MODE -> be.toggleDroneMode();
            case BTN_ROUND -> be.pressRound(player);
            case BTN_DIFFICULTY -> be.cycleDroneDifficulty();
            case BTN_VIEW_GRAVITY -> be.setDroneView(false);
            case BTN_VIEW_DRONES -> be.setDroneView(true);
            default -> { return false; }
        }
        return true;
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        return stillValid(access, player, ModBlocks.GRAVITY_CHAMBER.get());
    }

    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < INV_FIRST) {
            if (index >= DRONE_FIRST && roundRunning()) return ItemStack.EMPTY;
            if (!moveItemStackTo(stack, INV_FIRST, INV_END, true)) return ItemStack.EMPTY;
        } else if (stack.getItem() instanceof GravityUpgradeItem) {
            if (!moveItemStackTo(stack, 0, 1, false)) return ItemStack.EMPTY;
        } else if (stack.getItem() instanceof TrainingDroneItem) {
            if (roundRunning() || !moveItemStackTo(stack, DRONE_FIRST, DRONE_END, false)) return ItemStack.EMPTY;
        } else {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        if (stack.getCount() == original.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, stack);
        return original;
    }
}
