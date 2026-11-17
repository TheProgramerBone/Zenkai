package com.hmc.zenkai.client.gui.menu;

import com.hmc.zenkai.client.gui.ModMenuTypes;
import com.hmc.zenkai.content.blockentity.KiContainmentChamberBlockEntity;
import com.hmc.zenkai.feature.kichamber.KiEnergyType;
import com.hmc.zenkai.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
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
 * Menú de la cámara de contención de ki. DOS ZONAS dentro de this.slots:
 *   0..5    ranuras de la máquina: tolerancia Súper, Divina y Legendaria (fila de arriba) y
 *           velocidad, almacenamiento y batería (fila de abajo). El orden es el de las
 *           constantes SLOT_* de KiContainmentChamberBlockEntity.
 *   6..41   inventario y hotbar del jugador
 * LAS COORDENADAS DE LAS RANURAS SON PARTE DEL ASSET: cada una tiene su pozo pintado en
 * ki_chamber.png (tools/gen_ki_chamber_screen.py), en (x-1, y-1) y de 18x18.
 *
 * LAS CIFRAS VIAJAN PARTIDAS EN DOS: el paquete de ContainerData de vanilla manda cada valor como
 * un SHORT (16 bits), así que un tanque de 16 000 mB cabría por poco y un búfer de 100 000 FE
 * llegaría al cliente convertido en un número negativo. Cada cifra grande ocupa dos huecos
 * (mitad baja y mitad alta) y {@link #wide} las recompone.
 *
 * Los botones van por clickMenuButton, que es red vanilla del menú y ya valida que el jugador
 * tenga ESTE contenedor abierto: no hace falta packet propio (mismo criterio que ScouterBenchMenu).
 */
public class KiChamberMenu extends AbstractContainerMenu {

    // ── Huecos del ContainerData (un solo sitio para los dos lados) ──────────
    public static final int D_TANK_LO = 0, D_TANK_HI = 1;
    public static final int D_CAP_LO = 2, D_CAP_HI = 3;
    /** Ordinal de KiEnergyType del fluido del tanque; -1 = vacío. */
    public static final int D_TANK_TYPE = 4;
    public static final int D_FE_LO = 5, D_FE_HI = 6;
    public static final int D_FE_CAP_LO = 7, D_FE_CAP_HI = 8;
    public static final int D_FLAGS = 9;
    public static final int D_RATE_LO = 10, D_RATE_HI = 11;
    public static final int D_FE_RATE = 12;
    /** Calor de sobrecarga, 0..100. */
    public static final int D_HEAT = 13;
    public static final int DATA_SLOTS = 14;

    public static final int F_CHARGING = 1, F_FORCING = 2, F_CONVERT = 4;
    /** Puerta abierta, ajuste Forzar encendido y alguien sujeto dentro. */
    public static final int F_OPEN = 8, F_FORCE_SET = 16, F_OCCUPIED = 32;

    public static final int BTN_DOOR = 0, BTN_FORCE = 1, BTN_CONVERT = 2;

    public static final int MACHINE_SLOTS = KiContainmentChamberBlockEntity.UPGRADE_SLOTS;
    public static final int INV_FIRST = MACHINE_SLOTS, INV_END = MACHINE_SLOTS + 36;

    // ── Layout (debe coincidir con ki_chamber.png, que hereda la bandeja del generador) ──
    /** == SLOT_X / SLOT_Y de gen_ki_chamber_screen.py: tres columnas, dos filas. */
    public static final int SLOT_X = 64, SLOT_STEP = 18, SLOT_Y_TOP = 99, SLOT_Y_BOTTOM = 118;

    public static int slotX(int slot) { return SLOT_X + (slot % 3) * SLOT_STEP; }
    public static int slotY(int slot) { return slot < 3 ? SLOT_Y_TOP : SLOT_Y_BOTTOM; }
    private static final int INV_X = 47, INV_Y = 170, HOTBAR_Y = 229;

    private final ContainerData data;
    private final ContainerLevelAccess access;
    private final BlockPos pos;
    @Nullable private final KiContainmentChamberBlockEntity be;

    /** Cliente: el block entity no se usa; las lecturas llegan por ContainerData. */
    public KiChamberMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(id, inv, null, new ItemStackHandler(MACHINE_SLOTS), new SimpleContainerData(DATA_SLOTS),
                ContainerLevelAccess.NULL, buf.readBlockPos());
    }

    public KiChamberMenu(int id, Inventory inv, KiContainmentChamberBlockEntity be) {
        this(id, inv, be, be.upgradeHandler(), be.containerData(),
                ContainerLevelAccess.create(Objects.requireNonNull(be.getLevel()), be.getBlockPos()),
                be.getBlockPos());
    }

    private KiChamberMenu(int id, Inventory inv, @Nullable KiContainmentChamberBlockEntity be,
                          IItemHandler machine, ContainerData data, ContainerLevelAccess access, BlockPos pos) {
        super(ModMenuTypes.KI_CHAMBER.get(), id);
        this.be = be;
        this.data = data;
        this.access = access;
        this.pos = pos;

        // El filtro y el tope van en el Slot y no solo en el handler: en cliente el handler es
        // uno vacío sin reglas, y sin esto la pantalla dejaría soltar cualquier ítem (el servidor
        // lo rechazaría, pero el jugador vería el ítem parpadear dentro de la ranura).
        for (int i = 0; i < MACHINE_SLOTS; i++) {
            final int slot = i;
            addSlot(new SlotItemHandler(machine, slot, slotX(slot), slotY(slot)) {
                @Override
                public boolean mayPlace(@NotNull ItemStack stack) {
                    return KiContainmentChamberBlockEntity.validFor(slot, stack);
                }

                @Override
                public int getMaxStackSize() { return KiContainmentChamberBlockEntity.slotLimit(slot); }

                @Override
                public int getMaxStackSize(@NotNull ItemStack stack) {
                    return Math.min(stack.getMaxStackSize(), KiContainmentChamberBlockEntity.slotLimit(slot));
                }
            });
        }

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inv, col + row * 9 + 9, INV_X + col * 18, INV_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inv, col, INV_X + col * 18, HOTBAR_Y));
        }

        addDataSlots(data);
    }

    /** Posición de la mitad inferior de la cámara (la del block entity). */
    public BlockPos pos() { return pos; }

    // ── Lectura para la pantalla ─────────────────────────────────────────────

    /** Recompone una cifra partida en mitad baja y mitad alta. */
    private int wide(int lo, int hi) {
        return ((data.get(hi) & 0xFFFF) << 16) | (data.get(lo) & 0xFFFF);
    }

    public int tankAmount()   { return wide(D_TANK_LO, D_TANK_HI); }
    public int tankCapacity() { return Math.max(1, wide(D_CAP_LO, D_CAP_HI)); }
    public int energy()       { return wide(D_FE_LO, D_FE_HI); }
    public int energyCapacity() { return Math.max(1, wide(D_FE_CAP_LO, D_FE_CAP_HI)); }
    public int mbPerSecond()  { return wide(D_RATE_LO, D_RATE_HI); }
    public int fePerTick()    { return data.get(D_FE_RATE); }

    @Nullable
    public KiEnergyType tankType() { return KiEnergyType.byOrdinal(data.get(D_TANK_TYPE)); }

    public boolean isCharging()   { return (data.get(D_FLAGS) & F_CHARGING) != 0; }
    public boolean isForcing()    { return (data.get(D_FLAGS) & F_FORCING) != 0; }
    public boolean isConverting() { return (data.get(D_FLAGS) & F_CONVERT) != 0; }
    public boolean isOpen()       { return (data.get(D_FLAGS) & F_OPEN) != 0; }
    public boolean isForceSet()   { return (data.get(D_FLAGS) & F_FORCE_SET) != 0; }
    public boolean isOccupied()   { return (data.get(D_FLAGS) & F_OCCUPIED) != 0; }

    /** Calor de sobrecarga, 0..100. */
    public int heat() { return data.get(D_HEAT); }

    /** ¿La máquina tolera este tipo? Se lee de las ranuras, que ya se sincronizan solas. */
    public boolean tolerates(KiEnergyType type) {
        int slot = KiContainmentChamberBlockEntity.toleranceSlot(type);
        return slot < 0 || this.slots.get(slot).hasItem();
    }

    public float tankFraction()   { return Math.min(1f, tankAmount() / (float) tankCapacity()); }
    public float energyFraction() { return Math.min(1f, energy() / (float) energyCapacity()); }

    // ── Red ──────────────────────────────────────────────────────────────────

    @Override
    public boolean clickMenuButton(@NotNull Player player, int id) {
        if (be == null || !(player instanceof ServerPlayer sp)) return false;
        switch (id) {
            case BTN_DOOR -> be.toggleDoor();
            case BTN_FORCE -> be.toggleForce();
            case BTN_CONVERT -> be.toggleConvert();
            default -> { return false; }
        }
        return true;
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        return stillValid(access, player, ModBlocks.KI_CONTAINMENT_CHAMBER.get());
    }

    /**
     * Shift-clic. De la máquina al inventario siempre; del inventario, a la PRIMERA ranura que
     * acepte ese ítem (cada mejora tiene una sola ranura posible, así que no hay ambigüedad).
     * Lo que no es mejora ni batería no se mueve.
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
            for (int i = 0; i < MACHINE_SLOTS && !moved; i++) {
                if (KiContainmentChamberBlockEntity.validFor(i, stack)) moved = moveItemStackTo(stack, i, i + 1, false);
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
