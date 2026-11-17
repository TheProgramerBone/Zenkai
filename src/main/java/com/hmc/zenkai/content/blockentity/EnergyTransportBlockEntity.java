package com.hmc.zenkai.content.blockentity;

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
    private final IEnergyStorage view = new IEnergyStorage() {
        @Override public int receiveEnergy(int max, boolean sim) {
            EnergyNetwork net = energyNetwork();
            if (net != null) return net.receive(max, rate(), sim);
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
        return side == null || mode(side) == SideMode.NORMAL ? view : null;
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
    }

    @Override
    protected void loadAdditional(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider reg) {
        super.loadAdditional(tag, reg);
        energy = Math.max(0, tag.getInt("Energy"));
    }
}
