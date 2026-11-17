package com.hmc.zenkai.content.blockentity;

import com.hmc.zenkai.config.ServerConfig;
import com.hmc.zenkai.content.block.TransportBlock;
import com.hmc.zenkai.feature.transport.FluidNetwork;
import com.hmc.zenkai.feature.transport.SideMode;
import com.hmc.zenkai.feature.transport.TransportNetwork;
import com.hmc.zenkai.registry.ModBlockEntities;
import com.hmc.zenkai.registry.ModDataComponents;
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
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.SimpleFluidContent;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Tubería de fluidos y tanque de fluidos (misma clase, distinta capacidad y caudal). Un solo
 * tipo de fluido a la vez. El modelo de transferencia está en {@link TransportBlockEntity}. En una
 * TUBERÍA, {@link #tank} guarda solo su cuota del búfer de la red ({@link FluidNetwork}); la capability
 * habla con la red.
 */
public class FluidTransportBlockEntity extends TransportBlockEntity {

    /** La capacidad depende del bloque, que aún no se conoce al construir el campo: se fija en el
     *  constructor y se refresca cada tick. Ojo: FluidTank.fill lee su CAMPO de capacidad, no
     *  getCapacity(), así que sobrescribir el getter no limitaba nada (lo cazó un gametest). */
    private final FluidTank tank = new FluidTank(1000) {
        @Override protected void onContentsChanged() { setChanged(); }
    };

    public FluidTransportBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FLUID_TRANSPORT.get(), pos, state);
        tank.setCapacity(capacity());
    }

    @Override public TransportBlock.Kind kind() { return TransportBlock.Kind.FLUID; }

    public int capacity() { return isCable() ? ServerConfig.fluidPipeCapacity() : ServerConfig.fluidTankCapacity(); }
    public int rate()     { return isCable() ? ServerConfig.fluidPipeRate() : ServerConfig.fluidTankRate(); }
    /**
     * Depósito de ESTE bloque: su CUOTA del búfer de su red (tubería) o de su grupo (tanque), al día.
     * Para meter o sacar fluido desde fuera hay que usar {@link #fluidAt}: escribir aquí con el grupo
     * formado no lo vería el búfer del grupo.
     */
    public FluidTank tank() { syncShare(); return tank; }

    /** Acceso crudo para la red: sin sincronizar cuotas (la red es quien las escribe). */
    public FluidTank rawTank() { return tank; }

    @Override public long capacityLong() { return capacity(); }
    @Override public TransportNetwork.Accumulator newAccumulator() { return FluidNetwork.accumulator(); }

    /** Red de la tubería o grupo del tanque (null en el cliente). */
    public @Nullable FluidNetwork fluidNetwork() { return (FluidNetwork) network(); }

    /** El fluido que lleva: el de su red si la tiene, si no el de su depósito. Sin construir redes. */
    public FluidStack currentFluid() {
        return currentNetwork() instanceof FluidNetwork net ? net.fluid() : tank.getFluid();
    }

    @Override
    protected boolean compatibleContents(TransportBlockEntity o) {
        if (!(o instanceof FluidTransportBlockEntity f)) return false;
        FluidStack a = currentFluid(), b = f.currentFluid();
        return a.isEmpty() || b.isEmpty() || FluidStack.isSameFluidSameComponents(a, b);
    }

    /** Vista de capability: habla con la red o el grupo, con el caudal de ESTE bloque. */
    private final IFluidHandler pipeView = new IFluidHandler() {
        @Override public int getTanks() { return 1; }
        @Override public @NotNull FluidStack getFluidInTank(int i) {
            FluidNetwork net = fluidNetwork();
            return net != null ? net.fluid() : tank.getFluid();
        }
        @Override public int getTankCapacity(int i) {
            FluidNetwork net = fluidNetwork();
            return net != null ? (int) net.capacity() : capacity();
        }
        @Override public boolean isFluidValid(int i, @NotNull FluidStack stack) { return true; }
        @Override public int fill(@NotNull FluidStack resource, @NotNull FluidAction action) {
            FluidNetwork net = fluidNetwork();
            return net != null ? net.fill(resource, rate(), action) : 0;
        }
        @Override public @NotNull FluidStack drain(@NotNull FluidStack resource, @NotNull FluidAction action) {
            FluidNetwork net = fluidNetwork();
            return net != null ? net.drain(resource, rate(), action) : FluidStack.EMPTY;
        }
        @Override public @NotNull FluidStack drain(int max, @NotNull FluidAction action) {
            FluidNetwork net = fluidNetwork();
            return net != null ? net.drain(max, rate(), action) : FluidStack.EMPTY;
        }
    };

    /** Capability por cara: solo las caras NORMAL se ven desde fuera. */
    public @Nullable IFluidHandler fluidAt(@Nullable Direction side) {
        if (side != null && mode(side) != SideMode.NORMAL) return null;
        return pipeView;
    }

    @Override
    public Component info() {
        FluidNetwork net = fluidNetwork();
        if (net != null && (isCable() || net.size() > 1)) {
            FluidStack nf = net.fluid();
            String key = isCable() ? "messages.zenkai.transport.fluid_network" : "messages.zenkai.transport.fluid_tank_group";
            return nf.isEmpty()
                    ? Component.translatable(key + "_empty", net.capacity(), net.size())
                    : Component.translatable(key, nf.getHoverName(), nf.getAmount(), net.capacity(), net.size());
        }
        FluidStack fs = net != null ? net.fluid() : tank.getFluid();
        if (fs.isEmpty()) {
            return Component.translatable("messages.zenkai.transport.fluid_empty", capacity());
        }
        return Component.translatable("messages.zenkai.transport.fluid", fs.getHoverName(), fs.getAmount(), capacity());
    }

    // ── Tick ─────────────────────────────────────────────────────────────────

    public static void serverTick(Level level, BlockPos pos, BlockState state, FluidTransportBlockEntity be) {
        be.housekeeping();
        if (be.tank.getCapacity() != be.capacity()) be.tank.setCapacity(be.capacity());   // por si la config cambió
        // Cables y tanques: la red (o el grupo) tickea una vez por tick y la mueve su primer miembro.
        FluidNetwork net = be.fluidNetwork();
        if (net != null && net.isLeader(be)) net.tick(level);
    }

    // ── Componentes del ítem ──

    @Override
    protected void collectImplicitComponents(@NotNull DataComponentMap.Builder builder) {
        super.collectImplicitComponents(builder);
        if (isCable()) return;
        FluidStack share = tank().getFluid();   // la cuota de ESTE bloque, no la del grupo entero
        if (!share.isEmpty()) builder.set(ModDataComponents.TRANSPORT_FLUID.get(), SimpleFluidContent.copyOf(share));
    }

    @Override
    protected void applyImplicitComponents(@NotNull DataComponentInput input) {
        super.applyImplicitComponents(input);
        SimpleFluidContent content = input.get(ModDataComponents.TRANSPORT_FLUID.get());
        if (!isCable() && content != null) {
            dissolveNetwork();   // si ya había grupo, que escriba sus cuotas ANTES de fijar la nuestra
            tank.setFluid(content.copy());
        }
    }

    // ── NBT ──────────────────────────────────────────────────────────────────

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider reg) {
        super.saveAdditional(tag, reg);
        syncShare();
        tag.put("Tank", tank.writeToNBT(reg, new CompoundTag()));
    }

    @Override
    protected void loadAdditional(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider reg) {
        super.loadAdditional(tag, reg);
        if (tag.contains("Tank")) tank.readFromNBT(reg, tag.getCompound("Tank"));
    }
}
