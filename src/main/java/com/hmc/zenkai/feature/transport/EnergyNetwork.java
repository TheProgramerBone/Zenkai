package com.hmc.zenkai.feature.transport;

import com.hmc.zenkai.content.blockentity.EnergyTransportBlockEntity;
import com.hmc.zenkai.content.blockentity.TransportBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;

import java.util.ArrayList;
import java.util.List;

/**
 * Red de cables de energía o grupo de tanques de energía (multibloque). El modelo general está en
 * {@link TransportNetwork}.
 */
public final class EnergyNetwork extends TransportNetwork {

    private long energy;

    private EnergyNetwork(List<TransportBlockEntity> members, long capacity, long energy) {
        super(members, capacity);
        this.energy = Math.min(energy, capacity);
    }

    public static Accumulator accumulator() {
        return new Accumulator() {
            long sum;
            @Override public boolean admit(TransportBlockEntity be) {
                sum += ((EnergyTransportBlockEntity) be).rawEnergy();
                return true;
            }
            @Override public TransportNetwork create(List<TransportBlockEntity> members, long capacity) {
                return new EnergyNetwork(members, capacity, sum);
            }
        };
    }

    public long energy() { return energy; }

    // ── Capability de cada cable (con el caudal de ESE cable) ────────────────

    public int receive(int max, int rate, boolean simulate) {
        int in = (int) Math.min(Math.min(max, rate), capacity - energy);
        if (in <= 0) return 0;
        if (!simulate) { energy += in; markDirty(); }
        return in;
    }

    public int extract(int max, int rate, boolean simulate) {
        int out = (int) Math.min(Math.min(max, rate), energy);
        if (out <= 0) return 0;
        if (!simulate) { energy -= out; markDirty(); }
        return out;
    }

    @Override
    protected void writeShares() {
        long[] shares = split(energy, members, capacity);
        for (int i = 0; i < shares.length; i++) {
            ((EnergyTransportBlockEntity) members.get(i)).setRawEnergy((int) shares[i]);
        }
    }

    /** Suma (o resta) energía desde fuera: otra red o grupo que trata con este. */
    public void adjust(long delta) {
        if (delta == 0) return;
        energy = Math.max(0, Math.min(capacity, energy + delta));
        markDirty();
    }

    // ── Tick ─────────────────────────────────────────────────────────────────

    @Override
    protected void tickTransfer(Level level) {
        List<IEnergyStorage> outs = new ArrayList<>();
        List<Integer> outRates = new ArrayList<>();
        long before = energy;

        for (TransportBlockEntity m : members) {
            EnergyTransportBlockEntity me = (EnergyTransportBlockEntity) m;
            for (Direction d : Direction.values()) {
                SideMode mode = me.mode(d);
                if (mode == SideMode.NONE) continue;
                BlockPos np = me.getBlockPos().relative(d);
                if (contains(np) || !level.isLoaded(np)) continue;   // por dentro de la red o del grupo
                BlockEntity nb = level.getBlockEntity(np);
                if (nb instanceof EnergyTransportBlockEntity other) {
                    if (!tankGroup && !other.isCableBlock()) withTank(me, d, other);       // red -> tanque
                    else if (tankGroup && !other.isCableBlock()) tankToTank(me, d, other); // grupo -> otro grupo
                    continue;   // un cable frente a un grupo: lo trata la red del cable
                }
                IEnergyStorage target = level.getCapability(Capabilities.EnergyStorage.BLOCK, np, d.getOpposite());
                if (target == null) continue;
                if (mode == SideMode.PULL) {
                    if (!target.canExtract()) continue;
                    int want = (int) Math.min(me.rate(), capacity - energy);
                    if (want > 0) energy += target.extractEnergy(want, false);
                } else if (target.canReceive()) {
                    outs.add(target);
                    outRates.add(me.rate());
                }
            }
        }

        // Entrega a partes iguales entre los destinos, cada uno con el caudal de su bloque.
        for (int i = 0; i < outs.size() && energy > 0; i++) {
            long fair = Math.max(1, energy / (outs.size() - i));
            int offer = (int) Math.min(outRates.get(i), fair);
            energy -= outs.get(i).receiveEnergy(offer, false);
        }
        if (energy != before) markDirty();
    }

    /**
     * Red de cables frente a un tanque: las reglas de las dos caras, en los dos sentidos (el grupo del
     * tanque se salta los cables). Se equilibra el PORCENTAJE entre la red y el grupo entero.
     */
    private void withTank(EnergyTransportBlockEntity cable, Direction d, EnergyTransportBlockEntity tank) {
        EnergyNetwork g = tank.energyNetwork();
        if (g == null || g == this) return;
        SideMode mine = cable.mode(d);
        SideMode theirs = tank.mode(d.getOpposite());
        if (theirs == SideMode.NONE) return;
        long rate = Math.min(cable.rate(), tank.rate());
        long gRoom = g.capacity - g.energy;
        long amt;
        if (mine == SideMode.PULL) {
            if (!theirs.canGive()) return;
            amt = -Math.min(rate, Math.min(g.energy, capacity - energy));
        } else if (theirs == SideMode.PULL) {
            if (!mine.canGive()) return;
            amt = Math.min(rate, Math.min(energy, gRoom));
        } else {
            long eq = equalizeAmount(energy, capacity, g.energy, g.capacity);
            if (eq > 0 && mine.canGive() && theirs.canTake()) {
                amt = Math.min(rate, Math.min(eq, Math.min(energy, gRoom)));
            } else if (eq < 0 && theirs.canGive() && mine.canTake()) {
                amt = -Math.min(rate, Math.min(-eq, Math.min(g.energy, capacity - energy)));
            } else {
                return;
            }
        }
        if (amt == 0) return;
        energy -= amt;
        g.adjust(amt);
    }

    /**
     * Grupo de tanques frente a OTRO grupo: cada uno trata solo su lado (los dos tickean), como antes
     * entre dos tanques: dar si mi cara da y la suya toma (solo hasta igualar), o tirar en PULL.
     */
    private void tankToTank(EnergyTransportBlockEntity me, Direction d, EnergyTransportBlockEntity other) {
        EnergyNetwork g = other.energyNetwork();
        if (g == null || g == this) return;
        SideMode mine = me.mode(d);
        SideMode theirs = other.mode(d.getOpposite());
        if (theirs == SideMode.NONE) return;
        long rate = Math.min(me.rate(), other.rate());
        long amt;
        if (mine == SideMode.PULL) {
            if (!theirs.canGive()) return;
            amt = -Math.min(rate, Math.min(g.energy, capacity - energy));
        } else if (mine.canGive() && theirs.canTake()) {
            long eq = equalizeAmount(energy, capacity, g.energy, g.capacity);
            if (eq <= 0) return;
            amt = Math.min(rate, Math.min(eq, Math.min(energy, g.capacity - g.energy)));
        } else {
            return;
        }
        if (amt == 0) return;
        energy -= amt;
        g.adjust(amt);
    }
}
