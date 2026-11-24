package com.hmc.zenkai.content.blockentity;

import com.hmc.zenkai.client.gui.menu.ArmorFabricatorMenu;
import com.hmc.zenkai.content.block.ArmorFabricatorBlock;
import com.hmc.zenkai.feature.armor.ArmorFabricationRecipe;
import com.hmc.zenkai.feature.armor.ArmorSets;
import com.hmc.zenkai.feature.armor.AttributeTemplates;
import com.hmc.zenkai.feature.armor.KiInfusion;
import com.hmc.zenkai.feature.armor.TemplateDuplicationRecipe;
import com.hmc.zenkai.feature.kichamber.KiEnergyType;
import com.hmc.zenkai.registry.ModBlockEntities;
import com.hmc.zenkai.registry.ModRecipes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Fabricadora (Fase 5b de la 0.7.0; hasta el 2026-11-23 «máquina de fabricación» con tres pestañas). El usuario la partió
 * en dos para dar más hincapié a las recetas: aquí se CREAN cosas; lo que modifica una pieza ya hecha (imbuir, plantillas,
 * infundir) pasó a la {@link ImbuingStationBlockEntity}. Base común (mejoras, FE, tanque) en {@link KiMachineBlockEntity}.
 *
 * FABRICAR (modelo de DMZ, sin pieza vanilla de base): patrón (no se gasta) + pieza elegida con los botones +
 * material en 9 ranuras → la pieza del set, gastando FE a lo largo del tiempo de la receta
 * ({@link ArmorFabricationRecipe}). El fluido de Ki es un TURBO opcional (P6): con fluido de un tipo tolerado en el
 * tanque cada tick avanza el doble y gasta {@link #TURBO_MB_PER_TICK}. Las mejoras de velocidad multiplican el avance
 * (y el gasto de FE por tick en proporción: la pieza cuesta lo mismo, solo tarda menos).
 *
 * INFUSIÓN: la pieza fabricada recibe el nivel del katchin infundido con que se hizo (ítems propios por nivel) y sube su
 * durabilidad (KiInfusion). SIN MEZCLA: solo cuenta el material del mismo ítem que el de la primera ranura que casa.
 *
 * DUPLICAR plantillas: una plantilla en la ranura del patrón usa TemplateDuplicationRecipe; la plantilla se gasta y salen dos.
 *
 * Interruptor de fabricación (2026-11-22, pedido del usuario): con la fabricación PAUSADA no se gasta material ni FE
 * aunque esté todo puesto, para poder llenar las ranuras sin que la máquina se coma el material por error. El avance
 * se conserva.
 */
public class ArmorFabricatorBlockEntity extends KiMachineBlockEntity {

    // ── Ranuras (un solo sitio para block entity, menú y pantalla) ───────────
    // Al separar la estación de imbuido (2026-11-23) se quitaron las ranuras 15..20; MachineItemHandler suelta al mundo
    // lo que una fabricadora vieja tuviera en ellas.
    public static final int SLOT_PATTERN = UPGRADE_SLOTS;                                    // 4
    public static final int SLOT_MATERIAL_FIRST = SLOT_PATTERN + 1, MATERIAL_SLOTS = 9;     // 5..13
    public static final int SLOT_OUTPUT = SLOT_MATERIAL_FIRST + MATERIAL_SLOTS;              // 14
    public static final int SLOTS = SLOT_OUTPUT + 1;                                         // 15

    /** mB de fluido que gasta el turbo por tick de trabajo. */
    public static final int TURBO_MB_PER_TICK = 5;

    /** Piezas en el orden de los botones. */
    public static final ArmorItem.Type[] PIECES = {
            ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE, ArmorItem.Type.LEGGINGS, ArmorItem.Type.BOOTS };

    public static boolean validFor(int slot, ItemStack stack) {
        if (slot < UPGRADE_SLOTS) return upgradeValid(slot, stack);
        // Ranura del patrón: un patrón (fabricar) o una plantilla de atributos (duplicarla).
        if (slot == SLOT_PATTERN) return ArmorSets.setOf(stack) != null || AttributeTemplates.kindOf(stack) != null;
        if (slot == SLOT_OUTPUT) return false;
        return true;   // material: cualquier cosa (la receta decide si sirve)
    }

    public static int slotLimit(int slot) {
        if (slot < UPGRADE_SLOTS) return upgradeLimit(slot);
        // Patrón: uno; una pila de plantillas para duplicar también vale (se gasta de una en una).
        return 64;
    }

    private ArmorItem.Type selected = ArmorItem.Type.CHESTPLATE;
    private int progress;
    private int maxProgress;
    private boolean working, turbo;
    /** Interruptor de la fabricación (por defecto encendida, como hasta ahora). */
    private boolean craftEnabled = true;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            return switch (i) {
                case ArmorFabricatorMenu.D_PROGRESS -> progress;
                case ArmorFabricatorMenu.D_MAX_PROGRESS -> maxProgress;
                case ArmorFabricatorMenu.D_FLAGS -> (working ? ArmorFabricatorMenu.F_WORKING : 0)
                        | (turbo ? ArmorFabricatorMenu.F_TURBO : 0)
                        | (craftEnabled ? ArmorFabricatorMenu.F_ENABLED : 0);
                case ArmorFabricatorMenu.D_PIECE -> pieceIndex(selected);
                default -> sharedData(i);
            };
        }
        @Override public void set(int i, int v) { }
        @Override public int getCount() { return ArmorFabricatorMenu.DATA_SLOTS; }
    };

    public ArmorFabricatorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ARMOR_FABRICATOR.get(), pos, state, WORKSHOP_LIMITS, SLOTS, ArmorFabricatorBlockEntity::validFor,
                (slot, s) -> slot == SLOT_OUTPUT || validFor(slot, s), ArmorFabricatorBlockEntity::slotLimit);
    }

    public static int pieceIndex(ArmorItem.Type t) {
        for (int i = 0; i < PIECES.length; i++) if (PIECES[i] == t) return i;
        return 1;
    }

    // ── Accesos ──────────────────────────────────────────────────────────────
    public ContainerData containerData() { return data; }
    public ArmorItem.Type selectedPiece() { return selected; }
    public boolean craftEnabled() { return craftEnabled; }

    public void toggleCraft() {
        craftEnabled = !craftEnabled;
        setChanged();
    }

    public void selectPiece(int index) {
        if (index < 0 || index >= PIECES.length || PIECES[index] == selected) return;
        selected = PIECES[index];
        progress = 0;   // cambiar de pieza a mitad reinicia (el FE gastado se pierde, como sacar el patrón)
        setChanged();
    }

    // ── Tick ─────────────────────────────────────────────────────────────────

    public static void serverTick(Level level, BlockPos pos, BlockState state, ArmorFabricatorBlockEntity be) {
        be.tickShared(level, pos);
        be.tickFabrication(level);
        setLit(level, pos, state, be.working);
    }

    /** Receta para el patrón y la pieza puestos, o vacío. */
    public Optional<RecipeHolder<ArmorFabricationRecipe>> currentRecipe() {
        if (level == null) return Optional.empty();
        ResourceLocation set = ArmorSets.setOf(items.getStackInSlot(SLOT_PATTERN));
        if (set == null) return Optional.empty();
        return level.getRecipeManager().getRecipeFor(ModRecipes.ARMOR_FABRICATION.get(),
                new ArmorFabricationRecipe.Input(set, selected), level);
    }

    /** Cuántas unidades del material de la receta hay en las 9 ranuras. */
    public int materialCount(ArmorFabricationRecipe r) { return materialCount(r.material()); }

    /** Receta de duplicado para la plantilla puesta en la ranura del patrón, o vacío. */
    public Optional<RecipeHolder<TemplateDuplicationRecipe>> currentDuplication() {
        if (level == null) return Optional.empty();
        ItemStack t = items.getStackInSlot(SLOT_PATTERN);
        if (AttributeTemplates.kindOf(t) == null) return Optional.empty();
        return level.getRecipeManager().getRecipeFor(ModRecipes.TEMPLATE_DUPLICATION.get(),
                new TemplateDuplicationRecipe.Input(t), level);
    }

    /** Unidades del material que se usaría (el de la primera ranura que casa) en las 9 ranuras. */
    public int materialCount(net.minecraft.world.item.crafting.Ingredient material) {
        return materialCount(material, items::getStackInSlot);
    }

    /**
     * Ítem de material que manda: el de la PRIMERA ranura (en orden) que casa con la receta, o null. Sin mezcla de
     * niveles (rework del 2026-11-23): la receta acepta el katchin de cualquier nivel por tag, pero solo cuentan y se
     * gastan las unidades de ESE mismo ítem; ocho Súper y un Normal no hacen un peto de nueve.
     */
    @Nullable
    public static net.minecraft.world.item.Item leadMaterial(net.minecraft.world.item.crafting.Ingredient material,
                                                             java.util.function.IntFunction<ItemStack> slotGetter) {
        for (int i = 0; i < MATERIAL_SLOTS; i++) {
            ItemStack s = slotGetter.apply(SLOT_MATERIAL_FIRST + i);
            if (!s.isEmpty() && material.test(s)) return s.getItem();
        }
        return null;
    }

    /** Unidades del material que manda ({@link #leadMaterial}). Servidor y pantalla usan esta misma cuenta. */
    public static int materialCount(net.minecraft.world.item.crafting.Ingredient material,
                                    java.util.function.IntFunction<ItemStack> slotGetter) {
        net.minecraft.world.item.Item lead = leadMaterial(material, slotGetter);
        if (lead == null) return 0;
        int n = 0;
        for (int i = 0; i < MATERIAL_SLOTS; i++) {
            ItemStack s = slotGetter.apply(SLOT_MATERIAL_FIRST + i);
            if (s.is(lead) && material.test(s)) n += s.getCount();
        }
        return n;
    }

    /** Nivel de infusión con que saldría la pieza: el del material que manda (null = katchin normal). */
    @Nullable
    public static KiEnergyType infusionOf(net.minecraft.world.item.crafting.Ingredient material,
                                          java.util.function.IntFunction<ItemStack> slotGetter) {
        net.minecraft.world.item.Item lead = leadMaterial(material, slotGetter);
        return lead == null ? null : KiInfusion.materialLevel(new ItemStack(lead));
    }

    private void tickFabrication(Level level) {
        working = false;
        turbo = false;
        Optional<RecipeHolder<TemplateDuplicationRecipe>> dup = currentDuplication();
        if (dup.isPresent()) {
            TemplateDuplicationRecipe d = dup.get().value();
            work(level, d.time(), d.energyPerTick(), d.material(), d.count(), d.result(), () -> {
                consumeMaterial(d.material(), d.count());
                items.extractItem(SLOT_PATTERN, 1, false);   // la plantilla se gasta (el patrón no)
                return d.assemble(new TemplateDuplicationRecipe.Input(ItemStack.EMPTY), level.registryAccess());
            });
            return;
        }
        Optional<RecipeHolder<ArmorFabricationRecipe>> found = currentRecipe();
        if (found.isEmpty()) { reset(); return; }
        ArmorFabricationRecipe r = found.get().value();
        work(level, r.time(), r.energyPerTick(), r.material(), r.count(), r.result(), () -> {
            KiEnergyType inf = infusionOf(r.material(), items::getStackInSlot);
            consumeMaterial(r.material(), r.count());
            return KiInfusion.applyToArmor(r.assemble(new ArmorFabricationRecipe.Input(r.pattern(), r.piece()),
                    level.registryAccess()), inf);
        });
    }

    /**
     * Avance común de fabricar y duplicar: FE por tick, turbo con fluido tolerado, mejoras de velocidad y pausa. Al
     * completar llama a {@code finish} (gasta y devuelve lo que sale) y lo mete en la salida.
     */
    private void work(Level level, int time, int fePerTick, net.minecraft.world.item.crafting.Ingredient material, int count,
                      ItemStack preview, java.util.function.Supplier<ItemStack> finish) {
        maxProgress = time;
        if (!craftEnabled) return;   // pausada: ni material ni FE, y el avance se queda donde estaba
        if (!fitsOutput(SLOT_OUTPUT, preview) || materialCount(material) < count) { reset(); return; }

        int step = speedStep();
        KiEnergyType t = tankType();
        int turboMb = (int) fluidCost(TURBO_MB_PER_TICK);   // con eficiencia de fluido cuesta menos
        boolean canTurbo = t != null && tolerates(t) && tank.getFluidAmount() >= turboMb;
        if (canTurbo) step *= 2;
        step = Math.min(step, time - progress);
        long fe = (long) fePerTick * step;
        if (!energy.spend((int) fe)) return;   // sin FE se para, sin perder el avance

        if (canTurbo) {
            tank.drain(turboMb, IFluidHandler.FluidAction.EXECUTE);
            turbo = true;
        }
        working = true;
        progress += step;
        if (progress >= time) {
            pushOutput(SLOT_OUTPUT, finish.get());
            progress = 0;
        }
        setChanged();
    }

    private void reset() {
        if (progress != 0) { progress = 0; setChanged(); }
    }

    /** Gasta {@code count} unidades del material que manda (solo de ese ítem: sin mezcla de niveles). */
    private void consumeMaterial(net.minecraft.world.item.crafting.Ingredient material, int count) {
        net.minecraft.world.item.Item lead = leadMaterial(material, items::getStackInSlot);
        if (lead == null) return;
        int left = count;
        for (int i = 0; i < MATERIAL_SLOTS && left > 0; i++) {
            int slot = SLOT_MATERIAL_FIRST + i;
            ItemStack s = items.getStackInSlot(slot);
            if (!s.is(lead) || !material.test(s)) continue;
            int take = Math.min(left, s.getCount());
            items.extractItem(slot, take, false);
            left -= take;
        }
    }

    // ── Persistencia ─────────────────────────────────────────────────────────

    @Override
    protected void loadAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider reg) {
        super.loadAdditional(tag, reg);
        progress = tag.getInt("Progress");
        int p = tag.contains("Piece") ? tag.getInt("Piece") : 1;
        selected = PIECES[Math.max(0, Math.min(PIECES.length - 1, p))];
        craftEnabled = !tag.contains("CraftEnabled") || tag.getBoolean("CraftEnabled");   // máquinas viejas: encendida
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider reg) {
        super.saveAdditional(tag, reg);
        tag.putInt("Progress", progress);
        tag.putInt("Piece", pieceIndex(selected));
        tag.putBoolean("CraftEnabled", craftEnabled);
    }

    @Override
    public @NotNull Component getDisplayName() {
        return Component.translatable("screen.zenkai.armor_fabricator.title");
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, @NotNull Inventory inv, @NotNull Player player) {
        return new ArmorFabricatorMenu(id, inv, this);
    }
}
