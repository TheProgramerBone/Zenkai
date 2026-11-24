package com.hmc.zenkai.content.blockentity;

import com.hmc.zenkai.config.ServerConfig;
import com.hmc.zenkai.feature.kichamber.KiEnergyType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiPredicate;
import java.util.function.IntSupplier;
import java.util.function.IntUnaryOperator;

/**
 * Base de las máquinas de Ki que CONSUMEN FE y fluido de Ki (2026-11-23; nació al separar la máquina de fabricación y se
 * generalizó para que hacer una máquina nueva sea rellenar huecos). Hoy la usan la Fabricadora, la Estación de imbuido y
 * la Infusora de Ki. Guía paso a paso: docs/maquinas-y-energia.md, «Base común de las máquinas».
 *
 * Lo que da hecho:
 * - Ranuras 0..3 = las cuatro mejoras de la cámara de contención con su mismo significado (tolerancia, velocidad,
 *   almacenamiento, batería que DESCARGA en la máquina); el resto de ranuras las define cada máquina con sus
 *   validFor/slotLimit, sobre un {@link MachineItemHandler} (vuelve al tamaño actual al cargar y suelta lo que no encaja).
 * - Búfer de FE de solo entrada ({@link MachineEnergy}) y tanque de fluido de Ki, ambos ampliados por la mejora de
 *   almacenamiento; las capacidades base las da cada máquina (claves de ServerConfig propias).
 * - Los nueve primeros huecos de ContainerData (FE, tanque y su tipo: {@link #sharedData}) y los tooltips/barras que los
 *   leen en KiMachineMenu/TechPanelScreen.
 * - Persistencia de ranuras, tanque, FE y pestaña recordada; soltar el contenido al romperse; estado LIT del bloque;
 *   salida con apilado ({@link #fitsOutput}/{@link #pushOutput}); paso por tick con las mejoras de velocidad.
 *
 * Lo que queda para cada máquina: sus ranuras, su ContainerData propio a partir de {@link #SHARED_DATA}, el tick de su
 * trabajo (llamando antes a {@link #tickShared}), su menú y su nombre.
 */
public abstract class KiMachineBlockEntity extends BlockEntity implements MenuProvider {

    /**
     * Orden de las mejoras (2026-11-24, pedido del usuario: la batería siempre la última): tolerancia, velocidad y
     * almacenamiento (0..2, como la cámara de contención), eficiencia de fluido (3) y batería (4).
     */
    public static final int SLOT_EFFICIENCY = 3, SLOT_BATTERY = 4;
    public static final int UPGRADE_SLOTS = 5;
    /** Cuántas mejoras de eficiencia caben y cuánto recorta cada una del fluido de Ki que gasta la máquina. */
    public static final int MAX_EFFICIENCY = 4;
    public static final double EFFICIENCY_PER_UPGRADE = 0.15;
    /** Avance extra por cada mejora de velocidad (1 = +100 %). */
    public static final double SPEED_PER_UPGRADE = 1.0;
    /** ¿Qué ítem vale en una ranura de mejora de una máquina de Ki? Las cuatro de la cámara más la de eficiencia. */
    public static boolean upgradeValid(int slot, ItemStack stack) {
        if (slot == SLOT_EFFICIENCY) return stack.is(com.hmc.zenkai.registry.ModItems.MACHINE_EFFICIENCY_UPGRADE.get());
        if (slot == SLOT_BATTERY) return stack.is(com.hmc.zenkai.registry.ModItems.ENERGY_BATTERY.get());
        return KiContainmentChamberBlockEntity.validFor(slot, stack);
    }

    public static int upgradeLimit(int slot) {
        if (slot == SLOT_EFFICIENCY) return MAX_EFFICIENCY;
        return slot == SLOT_BATTERY ? 1 : KiContainmentChamberBlockEntity.slotLimit(slot);
    }

    /**
     * Fluido de Ki que cuesta de verdad una operación de {@code mb} con {@code n} mejoras de eficiencia: cada una quita
     * un 15 % y nunca baja de 1 mB (una operación con coste no se vuelve gratis). Estática para que las pantallas
     * calculen lo mismo que el servidor con la ranura sincronizada.
     */
    public static long discountedMb(long mb, int n) {
        if (mb <= 0 || n <= 0) return mb;
        double f = 1.0 - Math.min(MAX_EFFICIENCY, n) * EFFICIENCY_PER_UPGRADE;
        return Math.max(1L, Math.round(mb * f));
    }

    /** Ranuras de ContainerData que ocupa {@link #sharedData} (los menús empiezan sus datos propios aquí). */
    public static final int SHARED_DATA = 9;

    /** Capacidades base de una máquina (claves de ServerConfig), antes de las mejoras de almacenamiento. */
    public record Limits(IntSupplier feCapacity, IntSupplier maxReceive, IntSupplier tankCapacity) {}

    /** Las de la antigua máquina de fabricación, que heredan sus dos mitades (sin claves nuevas: regla de pocas opciones). */
    public static final Limits WORKSHOP_LIMITS = new Limits(ServerConfig::armorFabricatorFeCapacity,
            ServerConfig::armorFabricatorMaxReceive, ServerConfig::armorFabricatorTankCapacity);

    protected final MachineItemHandler items;
    protected final FluidTank tank;
    protected final MachineEnergy energy;
    private final Limits limits;

    /** Última pestaña abierta (cada menú define sus TAB_*), para reabrir en la misma. */
    protected int lastTab;

    protected KiMachineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, Limits limits, int slots,
                                   BiPredicate<Integer, ItemStack> valid, BiPredicate<Integer, ItemStack> keep,
                                   IntUnaryOperator limit) {
        super(type, pos, state);
        this.limits = limits;
        this.items = new MachineItemHandler(slots, valid, keep, limit, this::setChanged);
        this.tank = new FluidTank(limits.tankCapacity().getAsInt(), s -> KiEnergyType.ofFluid(s.getFluid()) != null) {
            @Override protected void onContentsChanged() { setChanged(); }
        };
        // Sin recorte al cargar: la mejora de almacenamiento amplía la capacidad y aún no la ha fijado al leer el NBT.
        this.energy = new MachineEnergy(MachineEnergy.Io.INPUT, limits.feCapacity(), limits.maxReceive(), false,
                this::setChanged);
    }

    // ── Accesos ──────────────────────────────────────────────────────────────
    public ItemStackHandler items() { return items; }
    public IFluidHandler fluidHandler() { return tank; }
    public FluidTank tank() { return tank; }
    public IEnergyStorage energyHandler() { return energy; }
    public int energyStored() { return energy.get(); }
    public int lastTab() { return lastTab; }

    public void setLastTab(int tab) {
        if (tab == lastTab) return;
        lastTab = tab;
        setChanged();
    }

    public boolean tolerates(KiEnergyType type) {
        return KiContainmentChamberBlockEntity.tolerates(items.getStackInSlot(KiContainmentChamberBlockEntity.SLOT_TOLERANCE), type);
    }

    @Nullable
    public KiEnergyType tankType() {
        return tank.isEmpty() ? null : KiEnergyType.ofFluid(tank.getFluid().getFluid());
    }

    /** Los nueve números de FE y tanque que viajan por ContainerData, en el orden de {@code KiMachineMenu.D_FE_LO..D_TANK_TYPE}. */
    protected int sharedData(int i) {
        return switch (i) {
            case 0 -> energy.get() & 0xFFFF;
            case 1 -> (energy.get() >>> 16) & 0xFFFF;
            case 2 -> energy.capacity() & 0xFFFF;
            case 3 -> (energy.capacity() >>> 16) & 0xFFFF;
            case 4 -> tank.getFluidAmount() & 0xFFFF;
            case 5 -> (tank.getFluidAmount() >>> 16) & 0xFFFF;
            case 6 -> tank.getCapacity() & 0xFFFF;
            case 7 -> (tank.getCapacity() >>> 16) & 0xFFFF;
            case 8 -> {
                KiEnergyType t = tankType();
                yield t == null ? -1 : t.ordinal();
            }
            default -> 0;
        };
    }

    // ── Tick común ───────────────────────────────────────────────────────────

    /** Lo que cada tick hace cualquier máquina antes de su trabajo propio. Servidor. */
    protected void tickShared(Level level, BlockPos pos) {
        items.dropSpill(level, pos);   // lo que no encajó al cargar una máquina con la distribución vieja
        applyUpgradeLimits();
        drainBattery();
    }

    /** Enciende o apaga la cara frontal (propiedad LIT, si el bloque la tiene) solo cuando cambia. */
    protected static void setLit(Level level, BlockPos pos, BlockState state, boolean lit) {
        if (state.hasProperty(BlockStateProperties.LIT) && state.getValue(BlockStateProperties.LIT) != lit) {
            level.setBlock(pos, state.setValue(BlockStateProperties.LIT, lit), 3);
        }
    }

    protected int speedUpgrades() {
        return Math.min(KiContainmentChamberBlockEntity.MAX_SPEED,
                items.getStackInSlot(KiContainmentChamberBlockEntity.SLOT_SPEED).getCount());
    }

    protected int efficiencyUpgrades() {
        return Math.min(MAX_EFFICIENCY, items.getStackInSlot(SLOT_EFFICIENCY).getCount());
    }

    /** Lo que cuesta realmente {@code mb} de fluido con las mejoras de eficiencia puestas. */
    protected long fluidCost(long mb) { return discountedMb(mb, efficiencyUpgrades()); }

    /** Pasos por tick de una carga o un trabajo con las mejoras de velocidad puestas. */
    protected int speedStep() {
        return 1 + (int) Math.round(speedUpgrades() * SPEED_PER_UPGRADE);
    }

    /**
     * Enganche para mejoras PROPIAS de una máquina (pregunta del usuario 2026-11-23: una mejora concreta no obliga a salir de
     * MachineEnergy). Multiplica la capacidad de FE y de tanque encima de la mejora de almacenamiento común; por defecto 1.
     * Para el caudal o el coste, la máquina ajusta su propio trabajo (p. ej. su paso o lo que pasa a energy.spend).
     */
    protected double extraCapacityFactor() { return 1.0; }

    private void applyUpgradeLimits() {
        int n = Math.min(KiContainmentChamberBlockEntity.MAX_STORAGE,
                items.getStackInSlot(KiContainmentChamberBlockEntity.SLOT_STORAGE).getCount());
        double f = (1.0 + n * ServerConfig.kiChamberStoragePercent() / 100.0) * extraCapacityFactor();
        int tankCap = (int) Math.min(Integer.MAX_VALUE / 2, limits.tankCapacity().getAsInt() * f);
        if (tank.getCapacity() != tankCap) {
            tank.setCapacity(tankCap);
            if (tank.getFluidAmount() > tankCap) tank.setFluid(new FluidStack(tank.getFluid().getFluid(), tankCap));
        }
        energy.setCapacity((int) Math.min(Integer.MAX_VALUE / 2, limits.feCapacity().getAsInt() * f));
    }

    private void drainBattery() {
        ItemStack battery = items.getStackInSlot(SLOT_BATTERY);
        if (battery.isEmpty() || energy.isFull()) return;
        IEnergyStorage cell = battery.getCapability(Capabilities.EnergyStorage.ITEM);
        if (cell == null || !cell.canExtract()) return;
        int room = Math.min(energy.space(), limits.maxReceive().getAsInt());
        int got = cell.extractEnergy(room, false);
        if (got > 0) { energy.generate(got); setChanged(); }
    }

    /** Cobra FE y fluido de una operación ya validada. */
    protected void pay(long fe, long mb) {
        energy.drain(fe);
        if (mb > 0) tank.drain((int) mb, IFluidHandler.FluidAction.EXECUTE);
    }

    // ── Salida ───────────────────────────────────────────────────────────────

    /**
     * ¿Cabe {@code result} en la ranura de salida? Vacía, o el mismo ítem con los mismos componentes y sitio (lo apilable
     * se apila; una armadura nunca, por su tope de 1).
     */
    protected boolean fitsOutput(int slot, ItemStack result) {
        ItemStack cur = items.getStackInSlot(slot);
        if (cur.isEmpty()) return true;
        return ItemStack.isSameItemSameComponents(cur, result) && cur.getCount() + result.getCount() <= cur.getMaxStackSize();
    }

    /** Mete {@code out} en la salida (comprobado antes con {@link #fitsOutput}). */
    protected void pushOutput(int slot, ItemStack out) {
        ItemStack cur = items.getStackInSlot(slot);
        if (cur.isEmpty()) items.setStackInSlot(slot, out);
        else { cur.grow(out.getCount()); setChanged(); }
    }

    // ── Persistencia ─────────────────────────────────────────────────────────

    @Override
    protected void loadAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider reg) {
        super.loadAdditional(tag, reg);
        if (tag.contains("Items")) {
            CompoundTag saved = tag.getCompound("Items").copy();
            // Layout 1 (cuatro mejoras, batería en la 3): la batería pasa a la 4 y lo que había desde la 4 se corre una.
            // Layout 2 (eficiencia en la 4, batería en la 3, solo unas horas): se intercambian la 3 y la 4.
            // Layout 3 = el actual. Sin esto una pieza de trabajo acabaría en una ranura de mejora.
            int layout = tag.getInt("UpgradeLayout");
            if (layout < 3 && saved.contains("Items", 9)) {
                net.minecraft.nbt.ListTag list = saved.getList("Items", 10);
                for (int i = 0; i < list.size(); i++) {
                    CompoundTag e = list.getCompound(i);
                    int slot = e.getInt("Slot");
                    if (layout < 2) {
                        e.putInt("Slot", slot == 3 ? 4 : slot > 3 ? slot + 1 : slot);
                    } else if (slot == 3 || slot == 4) {
                        e.putInt("Slot", 7 - slot);
                    }
                }
                if (layout < 2) saved.putInt("Size", saved.getInt("Size") + 1);
            }
            items.deserializeNBT(reg, saved);
        }
        if (tag.contains("Tank")) tank.readFromNBT(reg, tag.getCompound("Tank"));
        energy.load(tag);
        lastTab = tag.getInt("LastTab");
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider reg) {
        super.saveAdditional(tag, reg);
        tag.put("Items", items.serializeNBT(reg));
        tag.put("Tank", tank.writeToNBT(reg, new CompoundTag()));
        energy.save(tag);
        tag.putInt("LastTab", lastTab);
        tag.putInt("UpgradeLayout", 3);
    }

    public void dropContents(Level level, BlockPos pos) {
        items.dropSpill(level, pos);
        for (int i = 0; i < items.getSlots(); i++) {
            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), items.getStackInSlot(i));
        }
    }

    /** Solo para gametests: llena el búfer. */
    public void fillEnergyForTests(int fe) { energy.setForTests(fe); }
}
