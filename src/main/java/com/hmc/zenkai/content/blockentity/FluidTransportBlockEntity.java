package com.hmc.zenkai.content.blockentity;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidActionResult;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.items.ItemStackHandler;

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

    // ── Ranuras del tanque (pantalla TankScreen, 0.7.0) ───────────────────────
    /**
     * Dos parejas entrada -> salida, como el tanque de Mekanism: LLENAR (entra un recipiente vacío y
     * sale lleno del tanque) y VACIAR (entra uno lleno, sale vacío y su fluido pasa al tanque). Cubos
     * de cualquier fluido, también los de Ki, y cualquier ítem con la capability de fluido.
     */
    public static final int SLOT_FILL_IN = 0, SLOT_FILL_OUT = 1, SLOT_DRAIN_IN = 2, SLOT_DRAIN_OUT = 3, SLOTS = 4;

    private final ItemStackHandler items = new ItemStackHandler(SLOTS) {
        @Override protected void onContentsChanged(int slot) { setChanged(); }
        @Override public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return (slot == SLOT_FILL_IN || slot == SLOT_DRAIN_IN) && validFor(stack);
        }
    };

    public static boolean validFor(ItemStack stack) {
        return FluidUtil.getFluidHandler(stack).isPresent();
    }

    public ItemStackHandler items() { return items; }

    /**
     * El grupo entero SIN límite de caudal: un cubo son 1000 mB de golpe y la cara del tanque solo deja
     * pasar su caudal por tick. Lo usan las ranuras.
     */
    private final IFluidHandler groupView = new IFluidHandler() {
        @Override public int getTanks() { return 1; }
        @Override public @NotNull FluidStack getFluidInTank(int i) { return pipeView.getFluidInTank(i); }
        @Override public int getTankCapacity(int i) { return pipeView.getTankCapacity(i); }
        @Override public boolean isFluidValid(int i, @NotNull FluidStack stack) { return true; }
        @Override public int fill(@NotNull FluidStack r, @NotNull FluidAction a) {
            FluidNetwork net = fluidNetwork();
            return net != null ? net.fill(r, Integer.MAX_VALUE, a) : 0;
        }
        @Override public @NotNull FluidStack drain(@NotNull FluidStack r, @NotNull FluidAction a) {
            FluidNetwork net = fluidNetwork();
            return net != null ? net.drain(r, Integer.MAX_VALUE, a) : FluidStack.EMPTY;
        }
        @Override public @NotNull FluidStack drain(int max, @NotNull FluidAction a) {
            FluidNetwork net = fluidNetwork();
            return net != null ? net.drain(max, Integer.MAX_VALUE, a) : FluidStack.EMPTY;
        }
    };

    /** Una operación por pareja y tick, solo si la salida admite el resultado entero. */
    private void processSlots() {
        if (fluidNetwork() == null) return;
        transfer(SLOT_FILL_IN, SLOT_FILL_OUT, true);
        transfer(SLOT_DRAIN_IN, SLOT_DRAIN_OUT, false);
    }

    private void transfer(int in, int out, boolean fillContainer) {
        ItemStack src = items.getStackInSlot(in);
        if (src.isEmpty()) return;
        ItemStack one = src.copyWithCount(1);
        FluidActionResult sim = fillContainer
                ? FluidUtil.tryFillContainer(one, groupView, Integer.MAX_VALUE, null, false)
                : FluidUtil.tryEmptyContainer(one, groupView, Integer.MAX_VALUE, null, false);
        if (!sim.isSuccess()) return;
        // Primero se comprueba que la salida admite el producto ENTERO; si no, no se toca nada.
        if (!fitsOutput(out, sim.getResult())) return;
        FluidActionResult done = fillContainer
                ? FluidUtil.tryFillContainer(one, groupView, Integer.MAX_VALUE, null, true)
                : FluidUtil.tryEmptyContainer(one, groupView, Integer.MAX_VALUE, null, true);
        if (!done.isSuccess()) return;
        putOutput(out, done.getResult());
        items.extractItem(in, 1, false);
    }

    /*
     * La salida se escribe A MANO, no con insertItem: el handler rechaza cualquier ítem en las salidas
     * (isItemValid) para que el jugador no las use de entrada, y insertItem pasa por ese filtro. Con
     * insertItem el propio tanque no podía dejar el cubo lleno y la ranura nunca se movía (lo vieron
     * las capturas del 2026-11-17).
     */
    private boolean fitsOutput(int out, ItemStack product) {
        if (product.isEmpty()) return true;
        ItemStack cur = items.getStackInSlot(out);
        return cur.isEmpty() || (ItemStack.isSameItemSameComponents(cur, product)
                && cur.getCount() + product.getCount() <= Math.min(cur.getMaxStackSize(), items.getSlotLimit(out)));
    }

    private void putOutput(int out, ItemStack product) {
        if (product.isEmpty()) return;
        ItemStack cur = items.getStackInSlot(out);
        if (cur.isEmpty()) items.setStackInSlot(out, product.copy());
        else items.setStackInSlot(out, cur.copyWithCount(cur.getCount() + product.getCount()));
    }

    /** Cifras para el menú, partidas en 16 bits (ContainerData viaja como short); el fluido por id de registro. */
    public ContainerData containerData() {
        return new ContainerData() {
            @Override public int get(int i) {
                FluidNetwork net = fluidNetwork();
                FluidStack fs = net != null ? net.fluid() : tank.getFluid();
                long c = net != null ? net.capacity() : capacity();
                int ai = fs.getAmount(), ci = (int) Math.min(c, Integer.MAX_VALUE);
                return switch (i) {
                    case 0 -> ai & 0xFFFF;
                    case 1 -> ai >>> 16;
                    case 2 -> ci & 0xFFFF;
                    case 3 -> ci >>> 16;
                    case 4 -> net != null ? net.size() : 1;
                    case 5 -> fs.isEmpty() ? -1 : BuiltInRegistries.FLUID.getId(fs.getFluid());
                    default -> 0;
                };
            }
            @Override public void set(int i, int v) { }
            @Override public int getCount() { return 6; }
        };
    }

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
        if (!be.isCable()) be.processSlots();
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
        if (!isCable()) tag.put("Items", items.serializeNBT(reg));
    }

    @Override
    protected void loadAdditional(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider reg) {
        super.loadAdditional(tag, reg);
        if (tag.contains("Tank")) tank.readFromNBT(reg, tag.getCompound("Tank"));
        if (tag.contains("Items")) com.hmc.zenkai.util.ItemHandlers.loadKeepingSize(items, reg, tag.getCompound("Items"));
    }
}
