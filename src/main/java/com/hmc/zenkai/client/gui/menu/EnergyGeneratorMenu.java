package com.hmc.zenkai.client.gui.menu;

import com.hmc.zenkai.client.gui.ModMenuTypes;
import com.hmc.zenkai.content.blockentity.EnergyGeneratorBlockEntity;
import com.hmc.zenkai.feature.generator.GeneratorFuels;
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

import java.util.Objects;

/**
 * Menú del generador de energía.
 * DOS ZONAS dentro de this.slots:
 *   0..5    cola de combustible (3 columnas x 2 filas)
 *   6       hueco de carga (ítem con FE, bajo la barra de quemado)
 *   7..42   inventario y hotbar del jugador
 * LAS COORDENADAS SON PARTE DEL ASSET: cada hueco tiene su pozo pintado en
 * energy_generator.png, en (x-1, y-1) y de 18x18. Mover un número de aquí obliga a repintar la
 * textura. Es la misma regla que en ScouterBenchMenu y por el mismo motivo.
 */
public class EnergyGeneratorMenu extends AbstractContainerMenu {

    /** Huecos del ContainerData: energía, capacidad, ticks restantes, ticks totales, FE/tick,
     *  y las mitades ALTAS de energía (5) y capacidad (6): ver wide().
     *  UN solo número para los dos lados. Si el cliente y el servidor discrepan, el array se
     *  lee fuera de rango y revienta al abrir la GUI, no al compilar. */
    public static final int DATA_SLOTS = 7;

    public static final int FUEL_SLOTS = EnergyGeneratorBlockEntity.FUEL_SLOTS;

    /** Primer índice del inventario del jugador dentro de this.slots. */
    public static final int CHARGE_SLOT = FUEL_SLOTS;
    public static final int CHARGE_SLOTS = EnergyGeneratorBlockEntity.CHARGE_SLOTS;
    public static final int INV_FIRST = FUEL_SLOTS + CHARGE_SLOTS;
    public static final int INV_END   = FUEL_SLOTS + CHARGE_SLOTS + 36;

    // ── Layout (debe coincidir con energy_generator.png) ─────────────────────
    public static final int FUEL_X = 46, FUEL_Y = 39, FUEL_STEP = 22, FUEL_COLS = 3;
    /** Hueco de carga: bajo la barra de quemado, alineado con la primera columna de la cola. */
    /** Fila de carga: mismas columnas que la rejilla de combustible (== tools/gen_energy_generator_screen.py). */
    public static final int CHARGE_X = 46, CHARGE_Y = 100;
    private static final int INV_X = 47, INV_Y = 170, HOTBAR_Y = 229;

    private final ContainerData data;
    private final ContainerLevelAccess access;

    /** Cliente: el BE todavía no existe cuando se construye el menú, así que se levanta un
     *  handler vacío del mismo tamaño. Los ítems llegan por la sincronización normal del
     *  contenedor, igual que en cualquier cofre. */
    public EnergyGeneratorMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(id, inv, new ItemStackHandler(FUEL_SLOTS), new ItemStackHandler(CHARGE_SLOTS),
                new SimpleContainerData(DATA_SLOTS),
                ContainerLevelAccess.NULL, buf.readBlockPos());
    }

    public EnergyGeneratorMenu(int id, Inventory inv, EnergyGeneratorBlockEntity be) {
        this(id, inv, be.fuelHandler(), be.chargeHandler(), be.containerData(),
                ContainerLevelAccess.create(Objects.requireNonNull(be.getLevel()), be.getBlockPos()), be.getBlockPos());
    }

    private EnergyGeneratorMenu(int id, Inventory inv, IItemHandler fuel, IItemHandler charge, ContainerData data,
                                ContainerLevelAccess access, BlockPos pos) {
        super(ModMenuTypes.ENERGY_GENERATOR.get(), id);
        this.data = data;
        this.access = access;

        // Orden de filas y luego columnas: es el mismo que recorre igniteNext(), así que lo
        // que el jugador ve arriba-izquierda es literalmente lo primero que arde.
        for (int row = 0; row < FUEL_SLOTS / FUEL_COLS; row++) {
            for (int col = 0; col < FUEL_COLS; col++) {
                addSlot(new SlotItemHandler(fuel, row * FUEL_COLS + col,
                        FUEL_X + col * FUEL_STEP, FUEL_Y + row * FUEL_STEP) {
                    @Override
                    public boolean mayPlace(@NotNull ItemStack stack) {
                        return GeneratorFuels.isFuel(stack);
                    }
                });
            }
        }

        for (int i = 0; i < CHARGE_SLOTS; i++) {
            addSlot(new SlotItemHandler(charge, i, CHARGE_X + i * FUEL_STEP, CHARGE_Y) {
                @Override
                public boolean mayPlace(@NotNull ItemStack stack) {
                    return EnergyGeneratorBlockEntity.acceptsEnergy(stack);
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

    // ── Lectura para la pantalla ─────────────────────────────────────────────

    /**
     * Recompone una cifra que viaja partida en dos huecos (mitad baja y mitad alta).
     * El paquete de ContainerData de vanilla manda cada valor como un SHORT (16 bits). En un
     * mundo de un jugador no se nota, porque la conexión local pasa el objeto sin serializarlo,
     * pero en un servidor dedicado 100 000 FE llegaban al cliente como un número negativo y la
     * barra se quedaba vacía con el búfer lleno (arreglado el 2026-11-16).
     */
    private int wide(int lo, int hi) {
        return ((data.get(hi) & 0xFFFF) << 16) | (data.get(lo) & 0xFFFF);
    }

    public int energy()      { return wide(0, 5); }
    public int capacity()    { return wide(1, 6); }
    public int burnTicks()   { return data.get(2); }
    public int burnTotal()   { return data.get(3); }
    public int fePerTick()   { return data.get(4); }

    public boolean isBurning() { return burnTicks() > 0; }

    public float energyFraction() {
        int cap = capacity();
        return cap <= 0 ? 0f : (float) energy() / cap;
    }

    /** 1.0 recién encendido, 0.0 agotado. */
    public float burnFraction() {
        int total = burnTotal();
        return total <= 0 ? 0f : (float) burnTicks() / total;
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        return stillValid(access, player, ModBlocks.ENERGY_GENERATOR.get());
    }

    /**
     * Shift-click. Del inventario al generador SOLO si es combustible: sin esa comprobación,
     * mover un stack cualquiera lo metería en la cola y bloquearía el ciclo. Del generador al
     * inventario siempre, que es como se recuperan los cubos vacíos sin arrastrarlos.
     */
    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;

        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();

        if (index < INV_FIRST) {
            if (!moveItemStackTo(stack, INV_FIRST, INV_END, true)) return ItemStack.EMPTY;
        } else if (GeneratorFuels.isFuel(stack)) {
            if (!moveItemStackTo(stack, 0, FUEL_SLOTS, false)) return ItemStack.EMPTY;
        } else if (EnergyGeneratorBlockEntity.acceptsEnergy(stack)) {
            if (!moveItemStackTo(stack, CHARGE_SLOT, CHARGE_SLOT + CHARGE_SLOTS, false)) return ItemStack.EMPTY;
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