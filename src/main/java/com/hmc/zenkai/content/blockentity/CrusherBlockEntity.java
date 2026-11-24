package com.hmc.zenkai.content.blockentity;

import com.hmc.zenkai.client.gui.menu.CrusherMenu;
import com.hmc.zenkai.feature.crusher.CrushingRecipe;
import com.hmc.zenkai.feature.kichamber.KiEnergyType;
import com.hmc.zenkai.registry.ModBlockEntities;
import com.hmc.zenkai.registry.ModRecipes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * Triturador (Fase 5d de la 0.7.0; recetas y cifras guiadas por Create y Mekanism a petición del usuario, ver
 * pendientes-0.7.0 «Triturador»). Primera máquina hecha desde cero sobre {@link KiMachineBlockEntity}: una entrada, cuatro
 * salidas (la principal y los extras con probabilidad de {@link CrushingRecipe}), FE repartida en el tiempo de la receta.
 *
 * Fluido de Ki = TURBO opcional, igual que la Fabricadora (decisión de Claude por coherencia: el usuario dejó abierto si el
 * Triturador usa fluido): con un tipo tolerado en el tanque avanza el doble y gasta {@link #TURBO_MB_PER_TICK}. Sin fluido
 * trabaja igual. Mejoras de velocidad, almacenamiento, tolerancia y batería: las de la base.
 *
 * Antes de empezar comprueba que caben TODAS las salidas posibles (peor caso), para no perder un extra por falta de
 * sitio; a mitad, si se quita la entrada, se pierde el avance (como la Infusora). Tolvas: entran por la entrada y salen
 * por las cuatro salidas.
 */
public class CrusherBlockEntity extends KiMachineBlockEntity {

    public static final int SLOT_INPUT = UPGRADE_SLOTS;                                // 4
    public static final int SLOT_OUTPUT_FIRST = SLOT_INPUT + 1, OUTPUT_SLOTS = 4;      // 5..8
    public static final int SLOTS = SLOT_OUTPUT_FIRST + OUTPUT_SLOTS;                  // 9

    /** mB de fluido que gasta el turbo por tick de trabajo (== Fabricadora). */
    public static final int TURBO_MB_PER_TICK = 5;

    public static boolean validFor(int slot, ItemStack stack) {
        if (slot < UPGRADE_SLOTS) return upgradeValid(slot, stack);
        return slot == SLOT_INPUT;   // la entrada acepta cualquier cosa (la receta decide); las salidas, nada
    }

    public static int slotLimit(int slot) {
        if (slot < UPGRADE_SLOTS) return upgradeLimit(slot);
        return 64;
    }

    public static boolean isOutput(int slot) { return slot >= SLOT_OUTPUT_FIRST && slot < SLOTS; }

    /** Vista para tolvas y tuberías: 0 = entrada (solo meter), 1..4 = salidas (solo sacar); las mejoras no se tocan. */
    private final IItemHandler automation = new IItemHandler() {
        @Override public int getSlots() { return 1 + OUTPUT_SLOTS; }
        private int real(int slot) { return slot == 0 ? SLOT_INPUT : SLOT_OUTPUT_FIRST + slot - 1; }
        @Override public @NotNull ItemStack getStackInSlot(int slot) { return items.getStackInSlot(real(slot)); }
        @Override public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            return slot == 0 ? items.insertItem(SLOT_INPUT, stack, simulate) : stack;
        }
        @Override public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
            return slot > 0 ? items.extractItem(real(slot), amount, simulate) : ItemStack.EMPTY;
        }
        @Override public int getSlotLimit(int slot) { return 64; }
        @Override public boolean isItemValid(int slot, @NotNull ItemStack stack) { return slot == 0; }
    };

    private int progress, maxProgress;
    private boolean working, turbo;
    @Nullable private RecipeHolder<CrushingRecipe> cached;
    /** Receta con la que va el avance: si la entrada cambia a otra receta, el avance vuelve a 0. */
    @Nullable private net.minecraft.resources.ResourceLocation runningId;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            return switch (i) {
                case CrusherMenu.D_PROGRESS -> progress;
                case CrusherMenu.D_MAX_PROGRESS -> maxProgress;
                case CrusherMenu.D_FLAGS -> (working ? CrusherMenu.F_WORKING : 0) | (turbo ? CrusherMenu.F_TURBO : 0);
                default -> sharedData(i);
            };
        }
        @Override public void set(int i, int v) { }
        @Override public int getCount() { return CrusherMenu.DATA_SLOTS; }
    };

    public CrusherBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CRUSHER.get(), pos, state, WORKSHOP_LIMITS, SLOTS, CrusherBlockEntity::validFor,
                (slot, s) -> isOutput(slot) || validFor(slot, s), CrusherBlockEntity::slotLimit);
    }

    public IItemHandler automationHandler() { return automation; }
    public ContainerData containerData() { return data; }

    /** Receta para lo que hay en la entrada (con caché: la entrada cambia poco y buscar cada tick cuesta). */
    public Optional<RecipeHolder<CrushingRecipe>> currentRecipe() {
        if (level == null) return Optional.empty();
        ItemStack in = items.getStackInSlot(SLOT_INPUT);
        if (in.isEmpty()) return Optional.empty();
        CrushingRecipe.Input input = new CrushingRecipe.Input(in);
        if (cached != null && cached.value().matches(input, level)) return Optional.of(cached);
        Optional<RecipeHolder<CrushingRecipe>> found = level.getRecipeManager().getRecipeFor(ModRecipes.CRUSHING.get(), input, level);
        cached = found.orElse(null);
        return found;
    }

    // ── Tick ─────────────────────────────────────────────────────────────────

    public static void serverTick(Level level, BlockPos pos, BlockState state, CrusherBlockEntity be) {
        be.tickShared(level, pos);
        be.tickCrush(level);
        setLit(level, pos, state, be.working);
    }

    /** ¿Caben a la vez todas las salidas posibles de la receta en las cuatro ranuras? */
    private boolean outputsFit(List<ItemStack> outs) {
        ItemStack[] sim = new ItemStack[OUTPUT_SLOTS];
        for (int i = 0; i < OUTPUT_SLOTS; i++) sim[i] = items.getStackInSlot(SLOT_OUTPUT_FIRST + i).copy();
        for (ItemStack o : outs) {
            int left = o.getCount();
            for (int i = 0; i < OUTPUT_SLOTS && left > 0; i++) {
                ItemStack cur = sim[i];
                if (cur.isEmpty()) { sim[i] = o.copyWithCount(Math.min(left, o.getMaxStackSize())); left -= sim[i].getCount(); }
                else if (ItemStack.isSameItemSameComponents(cur, o)) {
                    int add = Math.min(left, cur.getMaxStackSize() - cur.getCount());
                    cur.grow(add);
                    left -= add;
                }
            }
            if (left > 0) return false;
        }
        return true;
    }

    private void insertOutput(ItemStack o) {
        ItemStack left = o.copy();
        for (int i = 0; i < OUTPUT_SLOTS && !left.isEmpty(); i++) {
            int slot = SLOT_OUTPUT_FIRST + i;
            ItemStack cur = items.getStackInSlot(slot);
            if (cur.isEmpty()) { items.setStackInSlot(slot, left); return; }
            if (ItemStack.isSameItemSameComponents(cur, left)) {
                int add = Math.min(left.getCount(), cur.getMaxStackSize() - cur.getCount());
                cur.grow(add);
                left.shrink(add);
            }
        }
        setChanged();
    }

    private void tickCrush(Level level) {
        working = false;
        turbo = false;
        Optional<RecipeHolder<CrushingRecipe>> found = currentRecipe();
        if (found.isEmpty()) { reset(); maxProgress = 0; return; }
        if (!found.get().id().equals(runningId)) { runningId = found.get().id(); reset(); }
        CrushingRecipe r = found.get().value();
        ItemStack in = items.getStackInSlot(SLOT_INPUT);
        maxProgress = r.time();
        if (!outputsFit(r.worstCase(in))) return;   // salida llena: se para conservando el avance

        int step = speedStep();
        KiEnergyType t = tankType();
        int turboMb = (int) fluidCost(TURBO_MB_PER_TICK);   // con eficiencia de fluido cuesta menos
        boolean canTurbo = t != null && tolerates(t) && tank.getFluidAmount() >= turboMb;
        if (canTurbo) step *= 2;
        step = Math.min(step, r.time() - progress);
        if (!energy.spend(r.energyPerTick() * step)) return;   // sin FE se para, sin perder el avance
        if (canTurbo) {
            tank.drain(turboMb, IFluidHandler.FluidAction.EXECUTE);
            turbo = true;
        }
        working = true;
        progress += step;
        if (progress >= r.time()) {
            for (ItemStack o : r.roll(in, level.random)) insertOutput(o);
            items.extractItem(SLOT_INPUT, r.count(), false);
            progress = 0;
        }
        setChanged();
    }

    private void reset() {
        if (progress != 0) { progress = 0; setChanged(); }
    }

    /** Termina la receta puesta al momento (gametests): mismo camino que el tick, sin FE ni espera. */
    public boolean crushNowForTests() {
        Optional<RecipeHolder<CrushingRecipe>> found = currentRecipe();
        if (found.isEmpty() || level == null) return false;
        CrushingRecipe r = found.get().value();
        ItemStack in = items.getStackInSlot(SLOT_INPUT);
        if (!outputsFit(r.worstCase(in))) return false;
        for (ItemStack o : r.roll(in, level.random)) insertOutput(o);
        items.extractItem(SLOT_INPUT, r.count(), false);
        return true;
    }

    // ── Persistencia y menú ──────────────────────────────────────────────────

    @Override
    protected void loadAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider reg) {
        super.loadAdditional(tag, reg);
        progress = tag.getInt("Progress");
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider reg) {
        super.saveAdditional(tag, reg);
        tag.putInt("Progress", progress);
    }

    @Override
    public @NotNull Component getDisplayName() {
        return Component.translatable("screen.zenkai.crusher.title");
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, @NotNull Inventory inv, @NotNull Player player) {
        return new CrusherMenu(id, inv, this);
    }
}
