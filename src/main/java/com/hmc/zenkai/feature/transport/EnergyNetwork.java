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
    /**
     * Vecinos que EMPUJARON energía a la red desde el último tick. No se les devuelve nada en ese tick:
     * un cubo o batería (Mekanism) que empuja y acepta por la misma cara se quedaba en un vaivén con el
     * cable y este nunca se vaciaba (siempre quedaba un resto en tránsito).
     */
    private final java.util.Set<BlockPos> sources = new java.util.HashSet<>();

    public void noteSource(BlockPos neighbour) { sources.add(neighbour.immutable()); }

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

    @Override protected double fillFraction() { return capacity > 0 ? (double) energy / capacity : 0; }

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
        List<TankLink> tankLinks = new ArrayList<>();

        for (TransportBlockEntity m : members) {
            EnergyTransportBlockEntity me = (EnergyTransportBlockEntity) m;
            for (Direction d : Direction.values()) {
                SideMode mode = me.mode(d);
                if (mode == SideMode.NONE) continue;
                BlockPos np = me.getBlockPos().relative(d);
                if (contains(np) || !level.isLoaded(np)) continue;   // por dentro de la red o del grupo
                BlockEntity nb = level.getBlockEntity(np);
                if (nb instanceof EnergyTransportBlockEntity other) {
                    if (!tankGroup && !other.isCableBlock()) collectLink(tankLinks, me, d, other);   // red <-> tanque
                    else if (tankGroup && !other.isCableBlock()) tankToTank(me, d, other); // grupo -> otro grupo
                    continue;   // un cable frente a un grupo: lo trata la red del cable
                }
                IEnergyStorage target = level.getCapability(Capabilities.EnergyStorage.BLOCK, np, d.getOpposite());
                if (target == null) continue;
                if (mode != SideMode.PULL && sources.contains(np)) continue;   // no se lo devolvemos al que lo empujó
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

        if (!tankLinks.isEmpty()) tankTransfer(tankLinks, !outs.isEmpty());

        // Entrega a partes iguales entre los destinos, cada uno con el caudal de su bloque.
        for (int i = 0; i < outs.size() && energy > 0; i++) {
            long fair = Math.max(1, energy / (outs.size() - i));
            int offer = (int) Math.min(outRates.get(i), fair);
            energy -= outs.get(i).receiveEnergy(offer, false);
        }
        sources.clear();
        if (energy != before) markDirty();
    }

    /** Una cara de la red que da a un tanque (o grupo de tanques). */
    private record TankLink(EnergyTransportBlockEntity cable, EnergyTransportBlockEntity tank,
                            EnergyNetwork g, SideMode mine, SideMode theirs, long rate) {
        double pct() { return g.capacity > 0 ? (double) g.energy / g.capacity : 0; }
    }

    private void collectLink(List<TankLink> out, EnergyTransportBlockEntity cable, Direction d, EnergyTransportBlockEntity tank) {
        EnergyNetwork g = tank.energyNetwork();
        if (g == null || g == this) return;
        SideMode theirs = tank.mode(d.getOpposite());
        if (theirs == SideMode.NONE) return;
        out.add(new TankLink(cable, tank, g, cable.mode(d), theirs, Math.min(cable.rate(), tank.rate())));
    }

    private void move(TankLink l, long amt) {   // amt > 0: red -> tanque; amt < 0: tanque -> red
        if (amt == 0) return;
        energy -= amt;
        l.g.adjust(amt);
    }

    /**
     * Red de cables frente a sus tanques. El cable es un CONDUCTO, no un depósito: antes equilibraba su
     * porcentaje con el del tanque y por eso siempre se quedaba con una fracción de su capacidad (un
     * cable de 4 000 frente a un tanque al 25 % guardaba 1 000 para siempre). Ahora:
     *  - PULL (de cualquiera de los dos lados): tira con el caudal, como siempre.
     *  - Entre caras que dan y toman: el cable ENTREGA todo lo que lleva al tanque con menos porcentaje
     *    que tenga hueco, y solo TOMA de un tanque si hay otro destino que lo necesite (una máquina, o otro
     *    tanque con menor porcentaje, y entonces solo hasta igualarlos). Con los tanques igualados o sin
     *    destinos, el cable queda vacío.
     */
    private void tankTransfer(List<TankLink> links, boolean outsWanted) {
        List<TankLink> normal = new ArrayList<>();
        for (TankLink l : links) {
            if (l.mine == SideMode.PULL) {
                if (!l.theirs.canGive()) continue;
                move(l, -Math.min(l.rate, Math.min(l.g.energy, capacity - energy)));
            } else if (l.theirs == SideMode.PULL) {
                if (!l.mine.canGive()) continue;
                move(l, Math.min(l.rate, Math.min(energy, l.g.capacity - l.g.energy)));
            } else {
                normal.add(l);
            }
        }
        java.util.Set<EnergyNetwork> pulledFrom = new java.util.HashSet<>();
        // 1) Tomar del tanque más lleno solo si alguien más lo necesita.
        for (TankLink src : normal) {
            if (!src.theirs.canGive() || !src.mine.canTake() || src.g.energy <= 0 || energy >= capacity) continue;
            long want = 0;
            if (outsWanted) {
                want = src.rate;
            } else {
                for (TankLink dst : normal) {
                    if (dst == src || dst.g == src.g || !dst.mine.canGive() || !dst.theirs.canTake()) continue;
                    if (dst.g.energy >= dst.g.capacity) continue;
                    want = Math.max(want, Math.min(src.rate, equalizeAmount(src.g.energy, src.g.capacity, dst.g.energy, dst.g.capacity)));
                }
            }
            long amt = Math.min(want, Math.min(src.g.energy, capacity - energy));
            if (amt > 0) { move(src, -amt); pulledFrom.add(src.g); }
        }
        // 2) Entregar todo al tanque con menor porcentaje (nunca al que se acaba de tomar).
        List<TankLink> sinks = new ArrayList<>(normal);
        sinks.sort(java.util.Comparator.comparingDouble(TankLink::pct));
        for (TankLink l : sinks) {
            if (energy <= 0) break;
            if (pulledFrom.contains(l.g) || !l.mine.canGive() || !l.theirs.canTake()) continue;
            move(l, Math.min(l.rate, Math.min(energy, l.g.capacity - l.g.energy)));
        }
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
