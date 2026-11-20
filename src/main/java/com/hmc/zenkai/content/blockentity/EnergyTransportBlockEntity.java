package com.hmc.zenkai.content.blockentity;

import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.ItemStackHandler;

import com.hmc.zenkai.config.ServerConfig;
import com.hmc.zenkai.content.block.TransportBlock;
import com.hmc.zenkai.feature.transport.EnergyNetwork;
import com.hmc.zenkai.feature.transport.SideMode;
import com.hmc.zenkai.feature.transport.TransportNetwork;
import com.hmc.zenkai.registry.ModBlockEntities;
import com.hmc.zenkai.registry.ModDataComponents;
import com.hmc.zenkai.util.ZenkaiNumbers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Cable de energía y tanque de energía (misma clase, distinta capacidad y caudal: los da el bloque).
 * El modelo de transferencia está explicado en {@link TransportBlockEntity}. En un CABLE, {@link #energy}
 * es solo su cuota del búfer de la red ({@link EnergyNetwork}); la capability habla con la red.
 */
public class EnergyTransportBlockEntity extends TransportBlockEntity {

    private int energy;

    /** Vista de capability. Una sola: el modo de la cara se decide al ENTREGAR la capability (null
     *  si la cara no es NORMAL) y se invalida al cambiarlo. En el servidor habla con la red o el grupo;
     *  el búfer propio solo se usa si no hay (cliente). */
    private final IEnergyStorage view = makeView(null);
    /** Una vista por cara: así la red sabe POR DÓNDE le entró la energía y no la devuelve por ahí. */
    private final IEnergyStorage[] sideViews = new IEnergyStorage[6];

    private IEnergyStorage makeView(@Nullable Direction from) {
        return new IEnergyStorage() {
        @Override public int receiveEnergy(int max, boolean sim) {
            EnergyNetwork net = energyNetwork();
            if (net != null) {
                int in = net.receive(max, rate(), sim);
                if (in > 0 && !sim && from != null) net.noteSource(getBlockPos().relative(from));
                return in;
            }
            int in = Math.min(Math.min(max, rate()), capacity() - energy);
            if (in > 0 && !sim) { energy += in; setChanged(); }
            return Math.max(in, 0);
        }
        @Override public int extractEnergy(int max, boolean sim) {
            EnergyNetwork net = energyNetwork();
            if (net != null) return net.extract(max, rate(), sim);
            int out = Math.min(Math.min(max, rate()), energy);
            if (out > 0 && !sim) { energy -= out; setChanged(); }
            return Math.max(out, 0);
        }
        @Override public int getEnergyStored() {
            EnergyNetwork net = energyNetwork();
            return net != null ? (int) Math.min(net.energy(), Integer.MAX_VALUE) : energy;
        }
        @Override public int getMaxEnergyStored() {
            EnergyNetwork net = energyNetwork();
            return net != null ? (int) Math.min(net.capacity(), Integer.MAX_VALUE) : capacity();
        }
        @Override public boolean canExtract() { return true; }
        @Override public boolean canReceive() { return true; }
        };
    }

    // ── Ranuras del tanque (pantalla TankScreen, 0.7.0) ───────────────────────
    /** Tres ranuras que se CARGAN desde el tanque (0-2) y tres que se DESCARGAN hacia él (3-5). Solo los
     *  tanques las usan. Eran una y una hasta el 2026-11-17; el usuario pidió aprovechar el hueco. */
    public static final int SLOT_CHARGE = 0, SLOT_DISCHARGE = 3, ROW = 3, SLOTS = 6;

    private final ItemStackHandler items = new ItemStackHandler(SLOTS) {
        @Override protected void onContentsChanged(int slot) { setChanged(); }
        @Override public boolean isItemValid(int slot, @NotNull ItemStack stack) { return validFor(stack); }
        @Override public int getSlotLimit(int slot) { return 1; }
    };

    /** Solo ítems con energía (baterías, herramientas de otros mods con FE). */
    public static boolean validFor(ItemStack stack) {
        return stack.getCapability(Capabilities.EnergyStorage.ITEM) != null;
    }

    public ItemStackHandler items() { return items; }

    /**
     * Carga y descarga de ítems, una vez por tick y con el caudal del tanque. Habla con el GRUPO
     * directamente (net.receive/extract), no con la vista de una cara.
     */
    private void processSlots() {
        EnergyNetwork net = energyNetwork();
        if (net == null) return;
        int rate = rate();
        for (int i = 0; i < ROW; i++) {
            IEnergyStorage charge = items.getStackInSlot(SLOT_CHARGE + i).getCapability(Capabilities.EnergyStorage.ITEM);
            if (charge != null && charge.canReceive()) {
                int can = net.extract(rate, rate, true);
                int took = charge.receiveEnergy(can, false);
                if (took > 0) net.extract(took, rate, false);
            }
            IEnergyStorage discharge = items.getStackInSlot(SLOT_DISCHARGE + i).getCapability(Capabilities.EnergyStorage.ITEM);
            if (discharge != null && discharge.canExtract()) {
                int room = net.receive(rate, rate, true);
                int got = discharge.extractEnergy(room, false);
                if (got > 0) net.receive(got, rate, false);
            }
        }
    }

    /** Cifras para el menú, partidas en 16 bits (ContainerData viaja como short). */
    public ContainerData containerData() {
        return new ContainerData() {
            @Override public int get(int i) {
                EnergyNetwork net = energyNetwork();
                long e = net != null ? net.energy() : energy;
                long c = net != null ? net.capacity() : capacity();
                int ei = (int) Math.min(e, Integer.MAX_VALUE), ci = (int) Math.min(c, Integer.MAX_VALUE);
                return switch (i) {
                    case 0 -> ei & 0xFFFF;
                    case 1 -> ei >>> 16;
                    case 2 -> ci & 0xFFFF;
                    case 3 -> ci >>> 16;
                    case 4 -> net != null ? net.size() : 1;
                    default -> 0;
                };
            }
            @Override public void set(int i, int v) { }
            @Override public int getCount() { return 5; }
        };
    }

    public EnergyTransportBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ENERGY_TRANSPORT.get(), pos, state);
    }

    @Override public TransportBlock.Kind kind() { return TransportBlock.Kind.ENERGY; }

    public int capacity() { return isCable() ? ServerConfig.energyCableCapacity() : ServerConfig.energyTankCapacity(); }
    public int rate()     { return isCable() ? ServerConfig.energyCableRate() : ServerConfig.energyTankRate(); }
    /** Energía de este bloque: su CUOTA del búfer de su red (cable) o de su grupo (tanque). */
    public int energy()   { syncShare(); return energy; }

    @Override public long capacityLong() { return capacity(); }
    @Override public TransportNetwork.Accumulator newAccumulator() { return EnergyNetwork.accumulator(); }

    /** Red del cable o grupo del tanque (null en el cliente). */
    public @Nullable EnergyNetwork energyNetwork() { return (EnergyNetwork) network(); }

    // Acceso crudo para la red: sin sincronizar cuotas (la red es quien las escribe).
    public int rawEnergy() { return energy; }
    public void setRawEnergy(int value) { energy = Math.max(0, value); }

    /** Capability por cara: solo las caras NORMAL se ven desde fuera. */
    public @Nullable IEnergyStorage energyAt(@Nullable Direction side) {
        if (side == null) return view;
        if (mode(side) != SideMode.NORMAL) return null;
        IEnergyStorage v = sideViews[side.ordinal()];
        if (v == null) v = sideViews[side.ordinal()] = makeView(side);
        return v;
    }

    @Override
    public Component info() {
        EnergyNetwork net = energyNetwork();
        if (net != null && (isCable() || net.size() > 1)) {
            return Component.translatable(isCable() ? "messages.zenkai.transport.energy_network" : "messages.zenkai.transport.energy_tank_group",
                    ZenkaiNumbers.screen(net.energy()), ZenkaiNumbers.screen(net.capacity()), net.size());
        }
        long stored = net != null ? net.energy() : energy;
        return Component.translatable("messages.zenkai.transport.energy",
                ZenkaiNumbers.screen(stored), ZenkaiNumbers.screen((long) capacity()));
    }

    // ── Tick ─────────────────────────────────────────────────────────────────

    /** Cables y tanques: la red (o el grupo) tickea una vez por tick y la mueve su primer miembro. */
    public static void serverTick(Level level, BlockPos pos, BlockState state, EnergyTransportBlockEntity be) {
        be.housekeeping();
        EnergyNetwork net = be.energyNetwork();
        if (net != null && net.isLeader(be)) net.tick(level);
        if (!be.isCable()) be.processSlots();
    }

    // ── Componentes del ítem ──

    @Override
    protected void collectImplicitComponents(@NotNull DataComponentMap.Builder builder) {
        super.collectImplicitComponents(builder);
        if (isCable()) return;
        int share = energy();   // la cuota de ESTE bloque: romper uno de un grupo se lleva solo lo suyo
        if (share > 0) builder.set(ModDataComponents.STORED_ENERGY.get(), share);
    }

    @Override
    protected void applyImplicitComponents(@NotNull DataComponentInput input) {
        super.applyImplicitComponents(input);
        Integer stored = input.get(ModDataComponents.STORED_ENERGY.get());
        if (!isCable() && stored != null) {
            dissolveNetwork();   // si ya había grupo, que escriba sus cuotas ANTES de fijar la nuestra
            energy = Math.max(0, Math.min(stored, capacity()));
        }
    }

    // ── NBT ──────────────────────────────────────────────────────────────────

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider reg) {
        super.saveAdditional(tag, reg);
        syncShare();
        tag.putInt("Energy", energy);
        if (!isCable()) tag.put("Items", items.serializeNBT(reg));
    }

    @Override
    protected void loadAdditional(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider reg) {
        super.loadAdditional(tag, reg);
        energy = Math.max(0, tag.getInt("Energy"));
        if (tag.contains("Items")) com.hmc.zenkai.util.ItemHandlers.loadKeepingSize(items, reg, tag.getCompound("Items"));
    }
}
