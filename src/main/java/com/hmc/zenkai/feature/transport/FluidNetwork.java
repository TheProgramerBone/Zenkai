package com.hmc.zenkai.feature.transport;

import com.hmc.zenkai.content.blockentity.FluidTransportBlockEntity;
import com.hmc.zenkai.content.blockentity.TransportBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Red de tuberías o grupo de tanques de fluidos (multibloque). UN solo fluido por red: dos tuberías con fluidos distintos no se unen (como
 * MechanicalPipe#isValidTransmitter de Mekanism) hasta que una se vacía. El modelo general está en
 * {@link TransportNetwork}.
 */
public final class FluidNetwork extends TransportNetwork {

    private FluidStack fluid;
    /** Un grupo de tanques se quedó vacío desde el último tick (ver el final de tickTransfer). */
    private boolean emptied;

    private FluidNetwork(List<TransportBlockEntity> members, long capacity, FluidStack fluid) {
        super(members, Math.min(capacity, Integer.MAX_VALUE));
        this.fluid = fluid.getAmount() > this.capacity ? fluid.copyWithAmount((int) this.capacity) : fluid;
    }

    public static Accumulator accumulator() {
        return new Accumulator() {
            FluidStack lock = FluidStack.EMPTY;
            long sum;
            @Override public boolean accepts(TransportBlockEntity be) {
                FluidStack now = ((FluidTransportBlockEntity) be).currentFluid();
                return lock.isEmpty() || now.isEmpty() || FluidStack.isSameFluidSameComponents(lock, now);
            }
            @Override public boolean admit(TransportBlockEntity be) {
                FluidStack share = ((FluidTransportBlockEntity) be).rawTank().getFluid();
                if (share.isEmpty()) return true;
                if (lock.isEmpty()) lock = share.copyWithAmount(1);
                else if (!FluidStack.isSameFluidSameComponents(lock, share)) return false;
                sum += share.getAmount();
                return true;
            }
            @Override public TransportNetwork create(List<TransportBlockEntity> members, long capacity) {
                FluidStack f = sum > 0 ? lock.copyWithAmount((int) Math.min(sum, Integer.MAX_VALUE)) : FluidStack.EMPTY;
                return new FluidNetwork(members, capacity, f);
            }
        };
    }

    public FluidStack fluid() { return fluid; }
    public int amount() { return fluid.getAmount(); }

    @Override protected double fillFraction() { return capacity > 0 ? (double) fluid.getAmount() / capacity : 0; }

    @Override protected String displayFluidId() {
        return fluid.isEmpty() ? "" : net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(fluid.getFluid()).toString();
    }

    private static boolean compatible(FluidStack a, FluidStack b) {
        return a.isEmpty() || b.isEmpty() || FluidStack.isSameFluidSameComponents(a, b);
    }

    private int room() { return (int) (capacity - fluid.getAmount()); }

    private void add(FluidStack in) {
        if (in.isEmpty()) return;
        fluid = fluid.isEmpty() ? in.copy() : fluid.copyWithAmount(fluid.getAmount() + in.getAmount());
        markDirty();
    }

    private FluidStack take(int amount) {
        int n = Math.min(amount, fluid.getAmount());
        if (n <= 0) return FluidStack.EMPTY;
        FluidStack out = fluid.copyWithAmount(n);
        int left = fluid.getAmount() - n;
        fluid = left > 0 ? fluid.copyWithAmount(left) : FluidStack.EMPTY;
        if (left == 0) emptied = true;   // también si lo vacía un cubo o una tubería, fuera del tick
        markDirty();
        return out;
    }

    // ── Capability de cada tubería (con el caudal de ESA tubería) ─────────────

    public int fill(FluidStack resource, int rate, IFluidHandler.FluidAction action) {
        if (resource.isEmpty() || !compatible(fluid, resource)) return 0;
        int n = Math.min(Math.min(resource.getAmount(), rate), room());
        if (n <= 0) return 0;
        if (action.execute()) add(resource.copyWithAmount(n));
        return n;
    }

    public @NotNull FluidStack drain(int max, int rate, IFluidHandler.FluidAction action) {
        int n = Math.min(Math.min(max, rate), fluid.getAmount());
        if (n <= 0) return FluidStack.EMPTY;
        return action.execute() ? take(n) : fluid.copyWithAmount(n);
    }

    public @NotNull FluidStack drain(FluidStack resource, int rate, IFluidHandler.FluidAction action) {
        if (resource.isEmpty() || fluid.isEmpty() || !FluidStack.isSameFluidSameComponents(fluid, resource)) return FluidStack.EMPTY;
        return drain(resource.getAmount(), rate, action);
    }

    @Override
    protected void writeShares() {
        long[] shares = split(fluid.getAmount(), members, capacity);
        for (int i = 0; i < shares.length; i++) {
            FluidStack s = shares[i] > 0 ? fluid.copyWithAmount((int) shares[i]) : FluidStack.EMPTY;
            ((FluidTransportBlockEntity) members.get(i)).rawTank().setFluid(s);
        }
    }

    // ── Tick ─────────────────────────────────────────────────────────────────

    @Override
    protected void tickTransfer(Level level) {
        List<IFluidHandler> outs = new ArrayList<>();
        List<Integer> outRates = new ArrayList<>();
        List<TankLink> tankLinks = new ArrayList<>();

        for (TransportBlockEntity m : members) {
            FluidTransportBlockEntity me = (FluidTransportBlockEntity) m;
            for (Direction d : Direction.values()) {
                SideMode mode = me.mode(d);
                if (mode == SideMode.NONE) continue;
                BlockPos np = me.getBlockPos().relative(d);
                if (contains(np) || !level.isLoaded(np)) continue;   // por dentro de la red o del grupo
                BlockEntity nb = level.getBlockEntity(np);
                if (nb instanceof FluidTransportBlockEntity other) {
                    if (!tankGroup && !other.isCableBlock()) collectLink(tankLinks, me, d, other);
                    else if (tankGroup && !other.isCableBlock()) tankToTank(me, d, other);
                    continue;
                }
                IFluidHandler target = level.getCapability(Capabilities.FluidHandler.BLOCK, np, d.getOpposite());
                if (target == null) continue;
                if (mode == SideMode.PULL) {
                    int want = Math.min(me.rate(), room());
                    if (want <= 0) continue;
                    FluidStack offered = fluid.isEmpty()
                            ? target.drain(want, IFluidHandler.FluidAction.SIMULATE)
                            : target.drain(fluid.copyWithAmount(want), IFluidHandler.FluidAction.SIMULATE);
                    if (offered.isEmpty() || !compatible(fluid, offered)) continue;
                    add(target.drain(offered, IFluidHandler.FluidAction.EXECUTE));
                } else if (!fluid.isEmpty()) {
                    outs.add(target);
                    outRates.add(me.rate());
                }
            }
        }

        if (!tankLinks.isEmpty()) tankTransfer(tankLinks, !outs.isEmpty());

        for (int i = 0; i < outs.size() && !fluid.isEmpty(); i++) {
            int fair = Math.max(1, fluid.getAmount() / (outs.size() - i));
            int offer = Math.min(outRates.get(i), fair);
            int accepted = outs.get(i).fill(fluid.copyWithAmount(offer), IFluidHandler.FluidAction.EXECUTE);
            if (accepted > 0) take(accepted);
        }

        // Un grupo de tanques que se queda vacío ya podría unirse a un vecino con otro fluido: se
        // disuelve y se reparte de nuevo. Solo en la transición (no en cada tick vacío), así no hay
        // vaivén. Al FINAL del tick: disolver a mitad escribiría cuotas que el resto del tick cambia.
        if (tankGroup && emptied && fluid.isEmpty()) invalidate();
        emptied = false;
    }

    /** Una cara de la red que da a un tanque (o grupo de tanques) con un fluido compatible. */
    private record TankLink(FluidNetwork g, SideMode mine, SideMode theirs, long rate) {
        double pct() { return g.capacity > 0 ? (double) g.amount() / g.capacity : 0; }
    }

    private void collectLink(List<TankLink> out, FluidTransportBlockEntity pipe, Direction d, FluidTransportBlockEntity tank) {
        FluidNetwork g = tank.fluidNetwork();
        if (g == null || g == this || !compatible(fluid, g.fluid)) return;
        SideMode theirs = tank.mode(d.getOpposite());
        if (theirs == SideMode.NONE) return;
        out.add(new TankLink(g, pipe.mode(d), theirs, Math.min(pipe.rate(), tank.rate())));
    }

    /**
     * Red de tuberias frente a sus tanques: conducto como en Mekanism, sin retener un porcentaje (ver
     * {@code EnergyNetwork.tankTransfer}, que es el mismo reparto). La tuberia ENTREGA todo al tanque con
     * menos porcentaje y hueco, y solo TOMA de un tanque si otro destino lo necesita.
     */
    private void tankTransfer(List<TankLink> links, boolean outsWanted) {
        List<TankLink> normal = new ArrayList<>();
        for (TankLink l : links) {
            if (l.mine == SideMode.PULL) {
                if (!l.theirs.canGive()) continue;
                moveTo(l.g, -Math.min(l.rate, Math.min(l.g.amount(), room())));
            } else if (l.theirs == SideMode.PULL) {
                if (!l.mine.canGive()) continue;
                moveTo(l.g, Math.min(l.rate, Math.min(amount(), l.g.room())));
            } else {
                normal.add(l);
            }
        }
        java.util.Set<FluidNetwork> pulledFrom = new java.util.HashSet<>();
        for (TankLink src : normal) {   // 1) tomar solo si alguien mas lo necesita
            if (!src.theirs.canGive() || !src.mine.canTake() || src.g.amount() <= 0 || room() <= 0) continue;
            if (!compatible(fluid, src.g.fluid)) continue;
            long want = 0;
            if (outsWanted) {
                want = src.rate;
            } else {
                for (TankLink dst : normal) {
                    if (dst.g == src.g || !dst.mine.canGive() || !dst.theirs.canTake() || dst.g.room() <= 0) continue;
                    if (!compatible(src.g.fluid, dst.g.fluid)) continue;
                    want = Math.max(want, Math.min(src.rate, equalizeAmount(src.g.amount(), src.g.capacity, dst.g.amount(), dst.g.capacity)));
                }
            }
            long amt = Math.min(want, Math.min(src.g.amount(), room()));
            if (amt > 0) { moveTo(src.g, -amt); pulledFrom.add(src.g); }
        }
        List<TankLink> sinks = new ArrayList<>(normal);   // 2) entregar todo al de menor porcentaje
        sinks.sort(java.util.Comparator.comparingDouble(TankLink::pct));
        for (TankLink l : sinks) {
            if (fluid.isEmpty()) break;
            if (pulledFrom.contains(l.g) || !l.mine.canGive() || !l.theirs.canTake() || !compatible(fluid, l.g.fluid)) continue;
            moveTo(l.g, Math.min(l.rate, Math.min(amount(), l.g.room())));
        }
    }

    /** Grupo frente a OTRO grupo: cada uno trata solo su lado, como antes entre dos tanques. */
    private void tankToTank(FluidTransportBlockEntity me, Direction d, FluidTransportBlockEntity other) {
        FluidNetwork g = other.fluidNetwork();
        if (g == null || g == this || !compatible(fluid, g.fluid)) return;
        SideMode mine = me.mode(d);
        SideMode theirs = other.mode(d.getOpposite());
        if (theirs == SideMode.NONE) return;
        int rate = Math.min(me.rate(), other.rate());
        long amt;
        if (mine == SideMode.PULL) {
            if (!theirs.canGive()) return;
            amt = -Math.min(rate, Math.min(g.amount(), room()));
        } else if (mine.canGive() && theirs.canTake()) {
            long eq = equalizeAmount(amount(), capacity, g.amount(), g.capacity);
            if (eq <= 0) return;
            amt = Math.min(rate, Math.min(eq, Math.min(amount(), g.room())));
        } else {
            return;
        }
        moveTo(g, amt);
    }

    /** Mueve {@code amt} mB a {@code g} (negativo: desde g). */
    private void moveTo(FluidNetwork g, long amt) {
        if (amt > 0) g.add(take((int) amt));
        else if (amt < 0) add(g.take((int) -amt));
    }
}
