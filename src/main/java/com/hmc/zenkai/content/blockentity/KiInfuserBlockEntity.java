package com.hmc.zenkai.content.blockentity;

import com.hmc.zenkai.client.gui.menu.KiInfuserMenu;
import com.hmc.zenkai.config.ServerConfig;
import com.hmc.zenkai.content.block.KiInfuserBlock;
import com.hmc.zenkai.feature.armor.KiInfusion;
import com.hmc.zenkai.feature.kichamber.KiEnergyType;
import com.hmc.zenkai.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Infusora de Ki (Fase 5c de la 0.7.0, §3.4): la tercera máquina del eje de armaduras. Un lingote de katchin (tag
 * {@code zenkai:ki_infusable}) entra, se carga con el fluido de Ki que haya en el tanque y sale infundido con ese tipo
 * ({@link KiInfusion}). Es la ÚNICA máquina que EXIGE fluido (P6): sin él no trabaja.
 *
 * Tolerancia (P6/P7): con un tipo sin su mejora de tolerancia en la ranura, la máquina NO trabaja y la pantalla lo dice,
 * igual que el turbo de la máquina de fabricación (precedente confirmado en juego). No hay sobrecarga explosiva aquí:
 * la de la cámara existe porque esa máquina GENERA el fluido con el jugador dentro; esta solo lo consume.
 *
 * Coste por lingote (constantes de KiInfusion, regla de pocas opciones): {@link KiInfusion#MB_PER_ITEM} mB del tipo,
 * cobrados al terminar, y {@link KiInfusion#FE_PER_ITEM} FE repartidos en {@link KiInfusion#TICKS} ticks. Si a mitad
 * falta FE se para sin perder el avance; si falta fluido o tolerancia, también (el avance se conserva).
 *
 * Un lingote ya infundido no se acepta: re-infundir para subir de tipo daría dos caminos al mismo resultado.
 * Ranuras de mejora: las seis de la cámara de contención con su significado; la batería DESCARGA en la máquina.
 */
public class KiInfuserBlockEntity extends BlockEntity implements MenuProvider {

    public static final int UPGRADE_SLOTS = KiContainmentChamberBlockEntity.UPGRADE_SLOTS;   // 0..5
    public static final int SLOT_INPUT = 6, SLOT_OUTPUT = 7;
    public static final int SLOTS = 8;
    /** Avance extra por cada mejora de velocidad (1 = +100 %), como la máquina de fabricación. */
    public static final double SPEED_PER_UPGRADE = 1.0;

    public static boolean validFor(int slot, ItemStack stack) {
        if (slot < UPGRADE_SLOTS) return KiContainmentChamberBlockEntity.validFor(slot, stack);
        if (slot == SLOT_INPUT) return canInfuse(stack);
        return false;   // salida
    }

    public static int slotLimit(int slot) {
        if (slot < UPGRADE_SLOTS) return KiContainmentChamberBlockEntity.slotLimit(slot);
        return 64;
    }

    /** ¿Se puede infundir este ítem? Del tag y aún sin infundir. */
    public static boolean canInfuse(ItemStack stack) {
        return !stack.isEmpty() && stack.is(KiInfusion.INFUSABLE) && KiInfusion.of(stack) == null;
    }

    private final ItemStackHandler items = new ItemStackHandler(SLOTS) {
        @Override protected void onContentsChanged(int slot) { setChanged(); }
        @Override public boolean isItemValid(int slot, @NotNull ItemStack stack) { return validFor(slot, stack); }
        @Override public int getSlotLimit(int slot) { return slotLimit(slot); }
    };

    /**
     * Vista para tolvas y tuberías de ítems: entra por la ranura de entrada y sale SOLO por la de salida; las mejoras
     * no se tocan desde fuera.
     */
    private final IItemHandler automation = new IItemHandler() {
        @Override public int getSlots() { return 2; }
        @Override public @NotNull ItemStack getStackInSlot(int slot) { return items.getStackInSlot(slot == 0 ? SLOT_INPUT : SLOT_OUTPUT); }
        @Override public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            return slot == 0 ? items.insertItem(SLOT_INPUT, stack, simulate) : stack;
        }
        @Override public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
            return slot == 1 ? items.extractItem(SLOT_OUTPUT, amount, simulate) : ItemStack.EMPTY;
        }
        @Override public int getSlotLimit(int slot) { return 64; }
        @Override public boolean isItemValid(int slot, @NotNull ItemStack stack) { return slot == 0 && canInfuse(stack); }
    };

    private final FluidTank tank = new FluidTank(ServerConfig.kiInfuserTankCapacity(),
            s -> KiEnergyType.ofFluid(s.getFluid()) != null) {
        @Override protected void onContentsChanged() { setChanged(); }
    };

    private final Energy energy = new Energy();
    private int progress;
    private boolean working;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            return switch (i) {
                case KiInfuserMenu.D_FE_LO -> energy.stored & 0xFFFF;
                case KiInfuserMenu.D_FE_HI -> (energy.stored >>> 16) & 0xFFFF;
                case KiInfuserMenu.D_FE_CAP_LO -> energy.capacity & 0xFFFF;
                case KiInfuserMenu.D_FE_CAP_HI -> (energy.capacity >>> 16) & 0xFFFF;
                case KiInfuserMenu.D_TANK_LO -> tank.getFluidAmount() & 0xFFFF;
                case KiInfuserMenu.D_TANK_HI -> (tank.getFluidAmount() >>> 16) & 0xFFFF;
                case KiInfuserMenu.D_CAP_LO -> tank.getCapacity() & 0xFFFF;
                case KiInfuserMenu.D_CAP_HI -> (tank.getCapacity() >>> 16) & 0xFFFF;
                case KiInfuserMenu.D_TANK_TYPE -> {
                    KiEnergyType t = tankType();
                    yield t == null ? -1 : t.ordinal();
                }
                case KiInfuserMenu.D_PROGRESS -> progress;
                case KiInfuserMenu.D_WORKING -> working ? 1 : 0;
                default -> 0;
            };
        }
        @Override public void set(int i, int v) { }
        @Override public int getCount() { return KiInfuserMenu.DATA_SLOTS; }
    };

    public KiInfuserBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.KI_INFUSER.get(), pos, state);
    }

    // ── Accesos ──────────────────────────────────────────────────────────────
    public ItemStackHandler items() { return items; }
    public IItemHandler automationHandler() { return automation; }
    public IFluidHandler fluidHandler() { return tank; }
    public FluidTank tank() { return tank; }
    public IEnergyStorage energyHandler() { return energy; }
    public ContainerData containerData() { return data; }
    public int energyStored() { return energy.stored; }
    public int progress() { return progress; }

    public boolean tolerates(KiEnergyType type) {
        int slot = KiContainmentChamberBlockEntity.toleranceSlot(type);
        return slot < 0 || !items.getStackInSlot(slot).isEmpty();
    }

    @Nullable
    public KiEnergyType tankType() {
        return tank.isEmpty() ? null : KiEnergyType.ofFluid(tank.getFluid().getFluid());
    }

    /** FE por tick de trabajo (hacia arriba: el lingote nunca sale más barato de lo que dice la constante). */
    public static int fePerTick() { return (int) Math.ceil(KiInfusion.FE_PER_ITEM / (double) KiInfusion.TICKS); }

    // ── Tick ─────────────────────────────────────────────────────────────────

    public static void serverTick(Level level, BlockPos pos, BlockState state, KiInfuserBlockEntity be) {
        be.applyUpgradeLimits();
        be.drainBattery();
        boolean was = be.working;
        be.tickInfuse();
        if (was != be.working && state.hasProperty(KiInfuserBlock.LIT)) {
            level.setBlock(pos, state.setValue(KiInfuserBlock.LIT, be.working), 3);
        }
    }

    private int speedUpgrades() {
        return Math.min(KiContainmentChamberBlockEntity.MAX_SPEED,
                items.getStackInSlot(KiContainmentChamberBlockEntity.SLOT_SPEED).getCount());
    }

    private void applyUpgradeLimits() {
        int n = Math.min(KiContainmentChamberBlockEntity.MAX_STORAGE,
                items.getStackInSlot(KiContainmentChamberBlockEntity.SLOT_STORAGE).getCount());
        double f = 1.0 + n * ServerConfig.kiChamberStoragePercent() / 100.0;
        int tankCap = (int) Math.min(Integer.MAX_VALUE / 2, ServerConfig.kiInfuserTankCapacity() * f);
        if (tank.getCapacity() != tankCap) {
            tank.setCapacity(tankCap);
            if (tank.getFluidAmount() > tankCap) tank.setFluid(new FluidStack(tank.getFluid().getFluid(), tankCap));
        }
        energy.capacity = (int) Math.min(Integer.MAX_VALUE / 2, ServerConfig.kiInfuserFeCapacity() * f);
        energy.stored = Math.min(energy.stored, energy.capacity);
    }

    private void drainBattery() {
        ItemStack battery = items.getStackInSlot(KiContainmentChamberBlockEntity.SLOT_BATTERY);
        if (battery.isEmpty() || energy.stored >= energy.capacity) return;
        IEnergyStorage cell = battery.getCapability(Capabilities.EnergyStorage.ITEM);
        if (cell == null || !cell.canExtract()) return;
        int room = Math.min(energy.capacity - energy.stored, ServerConfig.kiInfuserMaxReceive());
        int got = cell.extractEnergy(room, false);
        if (got > 0) { energy.stored += got; setChanged(); }
    }

    /** Por qué la máquina no trabaja ahora (o READY si puede). Lo comparten el tick y los gametests. */
    public enum State { READY, NO_INPUT, NO_FLUID, NO_TOLERANCE, OUTPUT_FULL, NO_ENERGY }

    public State state() {
        ItemStack in = items.getStackInSlot(SLOT_INPUT);
        if (!canInfuse(in)) return State.NO_INPUT;
        KiEnergyType t = tankType();
        if (t == null || tank.getFluidAmount() < KiInfusion.MB_PER_ITEM) return State.NO_FLUID;
        if (!tolerates(t)) return State.NO_TOLERANCE;
        if (!fitsOutput(KiInfusion.infused(in.copyWithCount(1), t))) return State.OUTPUT_FULL;
        if (energy.stored < fePerTick()) return State.NO_ENERGY;
        return State.READY;
    }

    private void tickInfuse() {
        working = false;
        State s = state();
        if (s == State.NO_INPUT) { reset(); return; }
        if (s != State.READY) return;   // se para conservando el avance

        int step = 1 + (int) Math.round(speedUpgrades() * SPEED_PER_UPGRADE);
        step = Math.min(step, KiInfusion.TICKS - progress);
        long fe = (long) fePerTick() * step;
        if (energy.stored < fe) return;
        energy.stored -= (int) fe;
        working = true;
        progress += step;
        if (progress >= KiInfusion.TICKS) finish();
        setChanged();
    }

    private void finish() {
        KiEnergyType t = tankType();
        ItemStack in = items.getStackInSlot(SLOT_INPUT);
        if (t == null || !canInfuse(in)) { progress = 0; return; }
        tank.drain(KiInfusion.MB_PER_ITEM, IFluidHandler.FluidAction.EXECUTE);
        ItemStack out = KiInfusion.infused(in.copyWithCount(1), t);
        items.extractItem(SLOT_INPUT, 1, false);
        ItemStack cur = items.getStackInSlot(SLOT_OUTPUT);
        if (cur.isEmpty()) items.setStackInSlot(SLOT_OUTPUT, out);
        else { cur.grow(1); setChanged(); }
        progress = 0;
    }

    private boolean fitsOutput(ItemStack result) {
        ItemStack cur = items.getStackInSlot(SLOT_OUTPUT);
        if (cur.isEmpty()) return true;
        return ItemStack.isSameItemSameComponents(cur, result) && cur.getCount() < cur.getMaxStackSize();
    }

    private void reset() {
        if (progress != 0) { progress = 0; setChanged(); }
    }

    // ── Persistencia ─────────────────────────────────────────────────────────

    @Override
    protected void loadAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider reg) {
        super.loadAdditional(tag, reg);
        if (tag.contains("Items")) items.deserializeNBT(reg, tag.getCompound("Items"));
        if (tag.contains("Tank")) tank.readFromNBT(reg, tag.getCompound("Tank"));
        energy.stored = Math.max(0, tag.getInt("Energy"));
        progress = tag.getInt("Progress");
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider reg) {
        super.saveAdditional(tag, reg);
        tag.put("Items", items.serializeNBT(reg));
        tag.put("Tank", tank.writeToNBT(reg, new CompoundTag()));
        tag.putInt("Energy", energy.stored);
        tag.putInt("Progress", progress);
    }

    public void dropContents(Level level, BlockPos pos) {
        for (int i = 0; i < items.getSlots(); i++) {
            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), items.getStackInSlot(i));
        }
    }

    @Override
    public @NotNull Component getDisplayName() {
        return Component.translatable("screen.zenkai.ki_infuser.title");
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, @NotNull Inventory inv, @NotNull Player player) {
        return new KiInfuserMenu(id, inv, this);
    }

    /** Solo para gametests: llena el búfer. */
    public void fillEnergyForTests(int fe) { energy.stored = Math.min(energy.capacity, fe); }

    // ── Búfer de FE: solo entrada, gasto interno ─────────────────────────────

    public final class Energy implements IEnergyStorage {
        int stored;
        int capacity = ServerConfig.kiInfuserFeCapacity();

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            int accepted = Math.min(capacity - stored, Math.min(ServerConfig.kiInfuserMaxReceive(), maxReceive));
            if (accepted <= 0) return 0;
            if (!simulate) { stored += accepted; setChanged(); }
            return accepted;
        }

        @Override public int extractEnergy(int maxExtract, boolean simulate) { return 0; }
        @Override public int getEnergyStored() { return stored; }
        @Override public int getMaxEnergyStored() { return capacity; }
        @Override public boolean canExtract() { return false; }
        @Override public boolean canReceive() { return true; }
    }
}
