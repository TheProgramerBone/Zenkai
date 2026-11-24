package com.hmc.zenkai.content.blockentity;

import com.hmc.zenkai.client.gui.menu.KiInfuserMenu;
import com.hmc.zenkai.config.ServerConfig;
import com.hmc.zenkai.feature.armor.KiInfusionRecipe;
import com.hmc.zenkai.feature.kichamber.KiEnergyType;
import com.hmc.zenkai.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.function.Predicate;

/**
 * Infusora de Ki (Fase 5c de la 0.7.0, §3.4): la tercera máquina del eje de armaduras. Un ítem entra, se carga con el
 * fluido de Ki que haya en el tanque y sale lo que diga su receta {@code zenkai:ki_infusion} ({@link KiInfusionRecipe},
 * por datapack al estilo del metallurgic_infusing de Mekanism desde el 2026-11-23). Las recetas de serie convierten el
 * lingote de katchin normal, o uno infundido de nivel inferior, en el lingote del nivel del fluido. Es la ÚNICA máquina
 * que EXIGE fluido (P6): sin él no trabaja.
 *
 * Tolerancia (P6/P7): con un tipo sin su mejora de tolerancia en la ranura, la máquina NO trabaja y la pantalla lo dice,
 * igual que el turbo de la máquina de fabricación (precedente confirmado en juego). No hay sobrecarga explosiva aquí:
 * la de la cámara existe porque esa máquina GENERA el fluido con el jugador dentro; esta solo lo consume.
 *
 * Coste: el de la receta. FE repartida en su tiempo, fluido cobrado al terminar. Si a mitad falta FE se para sin perder
 * el avance; si falta fluido o tolerancia, también (el avance se conserva). Si la entrada pasa a otra receta (otro ítem u
 * otro fluido en el tanque), el avance vuelve a 0, como en el Triturador.
 *
 * Un lingote ya infundido de nivel igual o superior al fluido no tiene receta: State.NOT_HIGHER («este Ki no infunde
 * eso»). Ranuras de mejora, FE, tanque y guardado: los de la base común {@link KiMachineBlockEntity}.
 */
public class KiInfuserBlockEntity extends KiMachineBlockEntity {

    public static final int SLOT_INPUT = UPGRADE_SLOTS, SLOT_OUTPUT = UPGRADE_SLOTS + 1;
    public static final int SLOTS = UPGRADE_SLOTS + 2;

    /** Capacidades propias (claves ki_infuser.* de ServerConfig). */
    private static final Limits LIMITS = new Limits(ServerConfig::kiInfuserFeCapacity, ServerConfig::kiInfuserMaxReceive,
            ServerConfig::kiInfuserTankCapacity);

    /** La entrada acepta cualquier cosa en el menú (la receta decide, como el Triturador); las tolvas filtran por receta. */
    public static boolean validFor(int slot, ItemStack stack) {
        if (slot < UPGRADE_SLOTS) return upgradeValid(slot, stack);
        return slot == SLOT_INPUT;   // salida: nada
    }

    public static int slotLimit(int slot) {
        if (slot < UPGRADE_SLOTS) return upgradeLimit(slot);
        return 64;
    }

    /**
     * Vista para tolvas y tuberías de ítems: entra por la ranura de entrada (solo lo que tenga receta, para no atascarla
     * con cualquier cosa) y sale SOLO por la de salida; las mejoras no se tocan desde fuera.
     */
    private final IItemHandler automation = new IItemHandler() {
        @Override public int getSlots() { return 2; }
        @Override public @NotNull ItemStack getStackInSlot(int slot) { return items.getStackInSlot(slot == 0 ? SLOT_INPUT : SLOT_OUTPUT); }
        @Override public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            return slot == 0 && isValid(stack) ? items.insertItem(SLOT_INPUT, stack, simulate) : stack;
        }
        @Override public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
            return slot == 1 ? items.extractItem(SLOT_OUTPUT, amount, simulate) : ItemStack.EMPTY;
        }
        @Override public int getSlotLimit(int slot) { return 64; }
        @Override public boolean isItemValid(int slot, @NotNull ItemStack stack) { return slot == 0 && isValid(stack); }
        private boolean isValid(ItemStack stack) { return level != null && KiInfusionRecipe.isInput(level.getRecipeManager(), stack); }
    };

    private int progress, maxProgress;
    private boolean working;
    @Nullable private RecipeHolder<KiInfusionRecipe> cached;
    /** Receta con la que va el avance: si cambia, el avance vuelve a 0. */
    @Nullable private ResourceLocation runningId;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            return switch (i) {
                case KiInfuserMenu.D_PROGRESS -> progress;
                case KiInfuserMenu.D_WORKING -> working ? 1 : 0;
                case KiInfuserMenu.D_MAX_PROGRESS -> maxProgress;
                default -> sharedData(i);
            };
        }
        @Override public void set(int i, int v) { }
        @Override public int getCount() { return KiInfuserMenu.DATA_SLOTS; }
    };

    public KiInfuserBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.KI_INFUSER.get(), pos, state, LIMITS, SLOTS, KiInfuserBlockEntity::validFor,
                (slot, s) -> slot == SLOT_OUTPUT || validFor(slot, s), KiInfuserBlockEntity::slotLimit);
    }

    // ── Accesos ──────────────────────────────────────────────────────────────
    public IItemHandler automationHandler() { return automation; }
    public ContainerData containerData() { return data; }
    public int progress() { return progress; }

    /** Receta para la entrada y el fluido actuales (con caché: cambian poco y buscar cada tick cuesta). */
    public Optional<RecipeHolder<KiInfusionRecipe>> currentRecipe() {
        if (level == null) return Optional.empty();
        ItemStack in = items.getStackInSlot(SLOT_INPUT);
        KiEnergyType t = tankType();
        if (in.isEmpty() || t == null) return Optional.empty();
        if (cached != null && cached.value().matches(new KiInfusionRecipe.Input(in, t), level)) return Optional.of(cached);
        Optional<RecipeHolder<KiInfusionRecipe>> found = KiInfusionRecipe.find(level.getRecipeManager(), in, t, level);
        cached = found.orElse(null);
        return found;
    }

    // ── Tick ─────────────────────────────────────────────────────────────────

    public static void serverTick(Level level, BlockPos pos, BlockState state, KiInfuserBlockEntity be) {
        be.tickShared(level, pos);   // vertedero, mejoras y batería
        be.tickInfuse();
        setLit(level, pos, state, be.working);
    }

    /** Por qué la máquina no trabaja ahora (o READY si puede). Lo comparten el tick, la pantalla y los gametests. */
    public enum State { READY, NO_INPUT, NO_FLUID, NOT_HIGHER, NO_TOLERANCE, OUTPUT_FULL, NO_ENERGY }

    /**
     * Mismas reglas en servidor y cliente (la pantalla las llama con lo que sincroniza el menú). {@code recipe} es la
     * de {@code (in, tank)}, o null si no hay.
     */
    public static State evaluate(RecipeManager rm, ItemStack in, @Nullable KiEnergyType tank, int tankAmount,
                                 @Nullable KiInfusionRecipe recipe, Predicate<KiEnergyType> tolerates,
                                 Predicate<ItemStack> fitsOutput, int energy) {
        return evaluate(rm, in, tank, tankAmount, recipe, tolerates, fitsOutput, energy, 0);
    }

    /** Igual, con {@code efficiency} mejoras de eficiencia de fluido (rebajan el fluido que pide la receta). */
    public static State evaluate(RecipeManager rm, ItemStack in, @Nullable KiEnergyType tank, int tankAmount,
                                 @Nullable KiInfusionRecipe recipe, Predicate<KiEnergyType> tolerates,
                                 Predicate<ItemStack> fitsOutput, int energy, int efficiency) {
        if (!KiInfusionRecipe.isInput(rm, in)) return State.NO_INPUT;
        if (tank == null || tankAmount <= 0) return State.NO_FLUID;
        if (recipe == null) return State.NOT_HIGHER;
        if (tankAmount < discountedMb(recipe.fluidAmount(), efficiency)) return State.NO_FLUID;
        if (!tolerates.test(tank)) return State.NO_TOLERANCE;
        if (!fitsOutput.test(recipe.output())) return State.OUTPUT_FULL;
        if (energy < recipe.energyPerTick()) return State.NO_ENERGY;
        return State.READY;
    }

    public State state() {
        if (level == null) return State.NO_INPUT;
        KiInfusionRecipe r = currentRecipe().map(RecipeHolder::value).orElse(null);
        return evaluate(level.getRecipeManager(), items.getStackInSlot(SLOT_INPUT), tankType(), tank.getFluidAmount(), r,
                this::tolerates, s -> fitsOutput(SLOT_OUTPUT, s), energy.get(), efficiencyUpgrades());
    }

    private void tickInfuse() {
        working = false;
        State s = state();
        if (s == State.NO_INPUT) { reset(); return; }
        if (s != State.READY) return;   // se para conservando el avance
        RecipeHolder<KiInfusionRecipe> h = currentRecipe().orElseThrow();
        KiInfusionRecipe r = h.value();
        if (!h.id().equals(runningId)) { runningId = h.id(); progress = 0; }
        maxProgress = r.time();

        int step = Math.min(speedStep(), r.time() - progress);
        if (!energy.spend(r.energyPerTick() * step)) return;
        working = true;
        progress += step;
        if (progress >= r.time()) finish(r);
        setChanged();
    }

    private void finish(KiInfusionRecipe r) {
        tank.drain((int) fluidCost(r.fluidAmount()), IFluidHandler.FluidAction.EXECUTE);
        items.extractItem(SLOT_INPUT, r.input().count(), false);
        pushOutput(SLOT_OUTPUT, r.output().copy());
        progress = 0;
    }

    private void reset() {
        if (progress != 0 || runningId != null) { progress = 0; runningId = null; setChanged(); }
    }

    // ── Persistencia ─────────────────────────────────────────────────────────

    @Override
    protected void loadAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider reg) {
        super.loadAdditional(tag, reg);
        progress = tag.getInt("Progress");
        maxProgress = tag.getInt("MaxProgress");
        runningId = tag.contains("Recipe") ? ResourceLocation.tryParse(tag.getString("Recipe")) : null;
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider reg) {
        super.saveAdditional(tag, reg);
        tag.putInt("Progress", progress);
        tag.putInt("MaxProgress", maxProgress);
        if (runningId != null) tag.putString("Recipe", runningId.toString());
    }

    @Override
    public @NotNull Component getDisplayName() {
        return Component.translatable("screen.zenkai.ki_infuser.title");
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, @NotNull Inventory inv, @NotNull Player player) {
        return new KiInfuserMenu(id, inv, this);
    }

}
