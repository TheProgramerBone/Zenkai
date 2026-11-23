package com.hmc.zenkai.content.blockentity;

import com.hmc.zenkai.client.gui.menu.ArmorFabricatorMenu;
import com.hmc.zenkai.config.ServerConfig;
import com.hmc.zenkai.content.block.ArmorFabricatorBlock;
import com.hmc.zenkai.content.item.BloodItem;
import com.hmc.zenkai.feature.ZenkaiAttributes;
import com.hmc.zenkai.feature.armor.ArmorFabricationRecipe;
import com.hmc.zenkai.feature.armor.ArmorImbuingRecipe;
import com.hmc.zenkai.feature.armor.ArmorSets;
import com.hmc.zenkai.feature.armor.ImbueCost;
import com.hmc.zenkai.feature.armor.ImbuedAttributes;
import com.hmc.zenkai.feature.kichamber.KiEnergyType;
import com.hmc.zenkai.registry.ModBlockEntities;
import com.hmc.zenkai.registry.ModDataComponents;
import com.hmc.zenkai.registry.ModRecipes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Máquina de fabricación (Fase 5b de la 0.7.0). Dos pestañas sobre el mismo bloque:
 *
 * FABRICAR (modelo de DMZ, sin pieza vanilla de base): patrón (no se gasta) + pieza elegida con los botones +
 * material en 9 ranuras → la pieza del set, gastando FE a lo largo del tiempo de la receta
 * ({@link ArmorFabricationRecipe}). El fluido de Ki es un TURBO opcional (P6): con fluido de un tipo tolerado en el
 * tanque cada tick avanza el doble y gasta {@link #TURBO_MB_PER_TICK}. Las mejoras de velocidad multiplican el avance
 * (y el gasto de FE por tick en proporción: la pieza cuesta lo mismo, solo tarda menos).
 *
 * ATRIBUTOS: pieza de armadura (cualquiera: vanilla, de otro mod o nuestra) + sangre → +calidad puntos del atributo
 * de esa sangre en {@code zenkai:imbued} (P13: conserva los atributos propios de la pieza). Coste (decisión 2026-11-16):
 * FE con la curva de {@link ImbueCost} + fluido de Ki + la sangre. El tipo de Ki lo dice la receta
 * {@link ArmorImbuingRecipe} de la pieza (sin receta = Base); un tipo superior vale gastando la mitad por cada tipo de
 * más (decisión del usuario 2026-11-22). Pulsar Imbuir arranca una CARGA corta ({@link #IMBUE_TICKS}, acortada por las
 * mejoras de velocidad) y el coste se cobra al final; si se quita la pieza o la sangre a mitad, se cancela sin gastar.
 * Así no se imbuye a base de spamear el botón (pedido del usuario 2026-11-22; antes era instantáneo).
 *
 * Interruptor de fabricación (2026-11-22, pedido del usuario): con la fabricación PAUSADA no se gasta material ni FE
 * aunque esté todo puesto, para poder llenar las ranuras sin que la máquina se coma el material por error. El avance
 * se conserva. La pestaña abierta por última vez se guarda en la máquina y viaja al abrir el menú.
 *
 * Ranuras de mejora: las seis de la cámara de contención con su mismo significado (P7; se reutilizan sus validadores),
 * salvo que aquí la batería DESCARGA en la máquina en vez de cargarse.
 */
public class ArmorFabricatorBlockEntity extends BlockEntity implements MenuProvider {

    // ── Ranuras (un solo sitio para block entity, menú y pantalla) ───────────
    public static final int UPGRADE_SLOTS = KiContainmentChamberBlockEntity.UPGRADE_SLOTS;   // 0..5
    public static final int SLOT_PATTERN = 6;
    public static final int SLOT_MATERIAL_FIRST = 7, MATERIAL_SLOTS = 9;                    // 7..15
    public static final int SLOT_OUTPUT = 16;
    public static final int SLOT_ARMOR = 17, SLOT_BLOOD = 18;
    public static final int SLOTS = 19;

    /** mB de fluido que gasta el turbo por tick de trabajo. */
    public static final int TURBO_MB_PER_TICK = 5;
    /** mB de fluido por punto imbuido bajo el umbral; pasado el umbral sube con el mismo factor que la FE. */
    public static final double IMBUE_MB_PER_POINT = 100.0;
    /** Ticks de la carga de imbuir sin mejoras (1,5 s, decisión del usuario 2026-11-22). */
    public static final int IMBUE_TICKS = 30;
    /** Avance extra por cada mejora de velocidad (1 = +100 %). */
    public static final double SPEED_PER_UPGRADE = 1.0;

    /** Piezas en el orden de los botones. */
    public static final ArmorItem.Type[] PIECES = {
            ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE, ArmorItem.Type.LEGGINGS, ArmorItem.Type.BOOTS };

    public static boolean validFor(int slot, ItemStack stack) {
        if (slot < UPGRADE_SLOTS) return KiContainmentChamberBlockEntity.validFor(slot, stack);
        if (slot == SLOT_PATTERN) return ArmorSets.setOf(stack) != null;
        if (slot == SLOT_OUTPUT) return false;
        if (slot == SLOT_ARMOR) return isArmorPiece(stack);
        if (slot == SLOT_BLOOD) return stack.getItem() instanceof BloodItem;
        return true;   // material: cualquier cosa (la receta decide si sirve)
    }

    public static int slotLimit(int slot) {
        if (slot < UPGRADE_SLOTS) return KiContainmentChamberBlockEntity.slotLimit(slot);
        if (slot == SLOT_PATTERN || slot == SLOT_ARMOR) return 1;
        return 64;
    }

    /** ¿Es una pieza de armadura de cabeza/pecho/piernas/pies? Vale cualquiera de cualquier mod. */
    public static boolean isArmorPiece(ItemStack stack) {
        if (stack.isEmpty()) return false;
        Equipable eq = Equipable.get(stack);
        return eq != null && eq.getEquipmentSlot().isArmor();
    }

    /** Tipo de Ki que pide imbuir esta pieza: el de su receta {@link ArmorImbuingRecipe}, o Base. */
    public static KiEnergyType requiredKiType(@Nullable Level level, ItemStack piece) {
        return ArmorImbuingRecipe.requiredFor(level, piece);
    }

    private final ItemStackHandler items = new ItemStackHandler(SLOTS) {
        @Override protected void onContentsChanged(int slot) { setChanged(); }
        @Override public boolean isItemValid(int slot, @NotNull ItemStack stack) { return validFor(slot, stack); }
        @Override public int getSlotLimit(int slot) { return slotLimit(slot); }
    };

    private final FluidTank tank = new FluidTank(ServerConfig.armorFabricatorTankCapacity(),
            s -> KiEnergyType.ofFluid(s.getFluid()) != null) {
        @Override protected void onContentsChanged() { setChanged(); }
    };

    private final Energy energy = new Energy();

    private ArmorItem.Type selected = ArmorItem.Type.CHESTPLATE;
    private int progress;
    private int maxProgress;
    private boolean working, turbo;
    /** Interruptor de la fabricación (por defecto encendida, como hasta ahora). */
    private boolean craftEnabled = true;
    /** Última pestaña abierta (ArmorFabricatorMenu.TAB_*), para reabrir en la misma. */
    private int lastTab;
    /** Carga de imbuir en curso (0 = parada); llega a IMBUE_TICKS. */
    private int imbueProgress;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            return switch (i) {
                case ArmorFabricatorMenu.D_FE_LO -> energy.stored & 0xFFFF;
                case ArmorFabricatorMenu.D_FE_HI -> (energy.stored >>> 16) & 0xFFFF;
                case ArmorFabricatorMenu.D_FE_CAP_LO -> energy.capacity() & 0xFFFF;
                case ArmorFabricatorMenu.D_FE_CAP_HI -> (energy.capacity() >>> 16) & 0xFFFF;
                case ArmorFabricatorMenu.D_TANK_LO -> tank.getFluidAmount() & 0xFFFF;
                case ArmorFabricatorMenu.D_TANK_HI -> (tank.getFluidAmount() >>> 16) & 0xFFFF;
                case ArmorFabricatorMenu.D_CAP_LO -> tank.getCapacity() & 0xFFFF;
                case ArmorFabricatorMenu.D_CAP_HI -> (tank.getCapacity() >>> 16) & 0xFFFF;
                case ArmorFabricatorMenu.D_TANK_TYPE -> {
                    KiEnergyType t = tankType();
                    yield t == null ? -1 : t.ordinal();
                }
                case ArmorFabricatorMenu.D_PROGRESS -> progress;
                case ArmorFabricatorMenu.D_MAX_PROGRESS -> maxProgress;
                case ArmorFabricatorMenu.D_FLAGS -> (working ? ArmorFabricatorMenu.F_WORKING : 0)
                        | (turbo ? ArmorFabricatorMenu.F_TURBO : 0)
                        | (craftEnabled ? ArmorFabricatorMenu.F_ENABLED : 0);
                case ArmorFabricatorMenu.D_PIECE -> pieceIndex(selected);
                case ArmorFabricatorMenu.D_IMBUE_PROGRESS -> imbueProgress;
                default -> 0;
            };
        }
        @Override public void set(int i, int v) { }
        @Override public int getCount() { return ArmorFabricatorMenu.DATA_SLOTS; }
    };

    public ArmorFabricatorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ARMOR_FABRICATOR.get(), pos, state);
    }

    public static int pieceIndex(ArmorItem.Type t) {
        for (int i = 0; i < PIECES.length; i++) if (PIECES[i] == t) return i;
        return 1;
    }

    // ── Accesos ──────────────────────────────────────────────────────────────
    public ItemStackHandler items() { return items; }
    public IFluidHandler fluidHandler() { return tank; }
    public FluidTank tank() { return tank; }
    public IEnergyStorage energyHandler() { return energy; }
    public ContainerData containerData() { return data; }
    public ArmorItem.Type selectedPiece() { return selected; }
    public int energyStored() { return energy.stored; }
    public boolean craftEnabled() { return craftEnabled; }
    public int lastTab() { return lastTab; }

    public void toggleCraft() {
        craftEnabled = !craftEnabled;
        setChanged();
    }

    public void setLastTab(int tab) {
        if (tab == lastTab) return;
        lastTab = tab;
        setChanged();
    }

    public boolean tolerates(KiEnergyType type) {
        int slot = KiContainmentChamberBlockEntity.toleranceSlot(type);
        return slot < 0 || !items.getStackInSlot(slot).isEmpty();
    }

    @Nullable
    public KiEnergyType tankType() {
        return tank.isEmpty() ? null : KiEnergyType.ofFluid(tank.getFluid().getFluid());
    }

    public void selectPiece(int index) {
        if (index < 0 || index >= PIECES.length || PIECES[index] == selected) return;
        selected = PIECES[index];
        progress = 0;   // cambiar de pieza a mitad reinicia (el FE gastado se pierde, como sacar el patrón)
        setChanged();
    }

    // ── Tick ─────────────────────────────────────────────────────────────────

    public static void serverTick(Level level, BlockPos pos, BlockState state, ArmorFabricatorBlockEntity be) {
        be.applyUpgradeLimits();
        be.drainBattery();
        boolean was = be.working;
        be.tickFabrication(level);
        be.tickImbue();
        if (was != be.working && state.hasProperty(ArmorFabricatorBlock.LIT)) {
            level.setBlock(pos, state.setValue(ArmorFabricatorBlock.LIT, be.working), 3);
        }
    }

    private int speedUpgrades() {
        return Math.min(KiContainmentChamberBlockEntity.MAX_SPEED,
                items.getStackInSlot(KiContainmentChamberBlockEntity.SLOT_SPEED).getCount());
    }

    private void applyUpgradeLimits() {
        int n = Math.min(KiContainmentChamberBlockEntity.MAX_STORAGE,
                items.getStackInSlot(KiContainmentChamberBlockEntity.SLOT_STORAGE).getCount());
        double f = 1.0 + n * ServerConfig.kiChamberStoragePercent() / 100.0;
        int tankCap = (int) Math.min(Integer.MAX_VALUE / 2, ServerConfig.armorFabricatorTankCapacity() * f);
        if (tank.getCapacity() != tankCap) {
            tank.setCapacity(tankCap);
            if (tank.getFluidAmount() > tankCap) tank.setFluid(new FluidStack(tank.getFluid().getFluid(), tankCap));
        }
        energy.capacity = (int) Math.min(Integer.MAX_VALUE / 2, ServerConfig.armorFabricatorFeCapacity() * f);
        energy.stored = Math.min(energy.stored, energy.capacity);
    }

    private void drainBattery() {
        ItemStack battery = items.getStackInSlot(KiContainmentChamberBlockEntity.SLOT_BATTERY);
        if (battery.isEmpty() || energy.stored >= energy.capacity) return;
        IEnergyStorage cell = battery.getCapability(Capabilities.EnergyStorage.ITEM);
        if (cell == null || !cell.canExtract()) return;
        int room = Math.min(energy.capacity - energy.stored, ServerConfig.armorFabricatorMaxReceive());
        int got = cell.extractEnergy(room, false);
        if (got > 0) { energy.stored += got; setChanged(); }
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
    public int materialCount(ArmorFabricationRecipe r) {
        int n = 0;
        for (int i = 0; i < MATERIAL_SLOTS; i++) {
            ItemStack s = items.getStackInSlot(SLOT_MATERIAL_FIRST + i);
            if (!s.isEmpty() && r.material().test(s)) n += s.getCount();
        }
        return n;
    }

    private void tickFabrication(Level level) {
        Optional<RecipeHolder<ArmorFabricationRecipe>> found = currentRecipe();
        working = false;
        turbo = false;
        if (found.isEmpty()) { reset(); return; }
        ArmorFabricationRecipe r = found.get().value();
        maxProgress = r.time();
        if (!craftEnabled) return;   // pausada: ni material ni FE, y el avance se queda donde estaba
        if (!items.getStackInSlot(SLOT_OUTPUT).isEmpty() || materialCount(r) < r.count()) { reset(); return; }

        int step = 1 + (int) Math.round(speedUpgrades() * SPEED_PER_UPGRADE);
        KiEnergyType t = tankType();
        boolean canTurbo = t != null && tolerates(t) && tank.getFluidAmount() >= TURBO_MB_PER_TICK;
        if (canTurbo) step *= 2;
        step = Math.min(step, r.time() - progress);
        long fe = (long) r.energyPerTick() * step;
        if (energy.stored < fe) return;   // sin FE se para, sin perder el avance

        energy.stored -= (int) fe;
        if (canTurbo) {
            tank.drain(TURBO_MB_PER_TICK, IFluidHandler.FluidAction.EXECUTE);
            turbo = true;
        }
        working = true;
        progress += step;
        if (progress >= r.time()) {
            consumeMaterial(r);
            items.setStackInSlot(SLOT_OUTPUT, r.assemble(new ArmorFabricationRecipe.Input(r.pattern(), r.piece()),
                    level.registryAccess()));
            progress = 0;
        }
        setChanged();
    }

    private void reset() {
        if (progress != 0) { progress = 0; setChanged(); }
    }

    private void consumeMaterial(ArmorFabricationRecipe r) {
        int left = r.count();
        for (int i = 0; i < MATERIAL_SLOTS && left > 0; i++) {
            int slot = SLOT_MATERIAL_FIRST + i;
            ItemStack s = items.getStackInSlot(slot);
            if (s.isEmpty() || !r.material().test(s)) continue;
            int take = Math.min(left, s.getCount());
            items.extractItem(slot, take, false);
            left -= take;
        }
    }

    // ── Imbuir ───────────────────────────────────────────────────────────────

    /** Coste de imbuir lo que hay puesto ahora; null si falta la pieza o la sangre. Lo usan el botón y la pantalla. */
    @Nullable
    public static ImbuePreview preview(@Nullable Level level, ItemStack piece, ItemStack blood) {
        if (!isArmorPiece(piece) || !(blood.getItem() instanceof BloodItem b)) return null;
        ZenkaiAttributes attr = b.attribute();
        int q = BloodItem.quality(blood);
        int cur = piece.getOrDefault(ModDataComponents.IMBUED.get(), ImbuedAttributes.NONE).get(attr);
        long fe = ImbueCost.totalCost(cur, q);
        long mb = ImbueCost.totalCost(cur, q, IMBUE_MB_PER_POINT,
                ServerConfig.imbueCostThreshold(), ServerConfig.imbueCostScale());
        return new ImbuePreview(attr, q, cur, fe, mb, requiredKiType(level, piece));
    }

    /**
     * {@code mb} = lo que cuesta con el tipo pedido ({@code fluid}). {@link #mbWith} da lo que cuesta con el tipo que hay
     * en el tanque (-1 si no vale): servidor y pantalla usan la misma cuenta.
     */
    public record ImbuePreview(ZenkaiAttributes attribute, int points, int current, long fe, long mb, KiEnergyType fluid) {
        public long mbWith(@Nullable KiEnergyType inTank) { return ArmorImbuingRecipe.scaledMb(mb, fluid, inTank); }
    }

    /** Resultado de pulsar Imbuir: OK o el motivo por el que no se pudo (clave de lang). */
    public enum ImbueResult {
        OK, NO_INPUT, NO_ENERGY, NO_FLUID;
        public String langKey() { return "screen.zenkai.armor_fabricator.imbue." + name().toLowerCase(java.util.Locale.ROOT); }
    }

    /**
     * Botón Imbuir: comprueba que se pueda (mismos motivos que {@link #imbue}) y arranca la carga. Si ya hay una en curso
     * no hace nada. El coste se cobra al acabar.
     */
    public ImbueResult startImbue() {
        if (imbueProgress > 0) return ImbueResult.OK;
        ImbueResult check = canImbue();
        if (check == ImbueResult.OK) {
            imbueProgress = 1;
            setChanged();
        }
        return check;
    }

    public boolean imbuing() { return imbueProgress > 0; }

    private void tickImbue() {
        if (imbueProgress <= 0) return;
        if (canImbue() != ImbueResult.OK) {   // pieza o sangre quitada, o se quedó sin FE/fluido a mitad: se cancela
            imbueProgress = 0;
            setChanged();
            return;
        }
        imbueProgress += 1 + (int) Math.round(speedUpgrades() * SPEED_PER_UPGRADE);
        if (imbueProgress >= IMBUE_TICKS) {
            imbueProgress = 0;
            imbue();
        }
        setChanged();
    }

    /** ¿Se podría imbuir ahora mismo? OK o el motivo. */
    private ImbueResult canImbue() {
        ImbuePreview p = preview(level, items.getStackInSlot(SLOT_ARMOR), items.getStackInSlot(SLOT_BLOOD));
        if (p == null) return ImbueResult.NO_INPUT;
        if (energy.stored < p.fe()) return ImbueResult.NO_ENERGY;
        long mb = p.mbWith(tankType());
        if (mb < 0 || tank.getFluidAmount() < mb) return ImbueResult.NO_FLUID;
        return ImbueResult.OK;
    }

    /** Imbuye una vez (una sangre) al momento, sin carga: lo llama el final de la carga (y los gametests). Servidor. */
    public ImbueResult imbue() {
        ItemStack piece = items.getStackInSlot(SLOT_ARMOR);
        ItemStack blood = items.getStackInSlot(SLOT_BLOOD);
        ImbuePreview p = preview(level, piece, blood);
        if (p == null) return ImbueResult.NO_INPUT;
        if (energy.stored < p.fe()) return ImbueResult.NO_ENERGY;
        long mb = p.mbWith(tankType());
        if (mb < 0 || tank.getFluidAmount() < mb) return ImbueResult.NO_FLUID;

        energy.stored -= (int) p.fe();
        tank.drain((int) mb, IFluidHandler.FluidAction.EXECUTE);
        items.extractItem(SLOT_BLOOD, 1, false);
        ItemStack out = piece.copy();
        ImbuedAttributes before = out.getOrDefault(ModDataComponents.IMBUED.get(), ImbuedAttributes.NONE);
        out.set(ModDataComponents.IMBUED.get(), before.plus(p.attribute(), p.points()));
        items.setStackInSlot(SLOT_ARMOR, out);
        setChanged();
        return ImbueResult.OK;
    }

    // ── Persistencia ─────────────────────────────────────────────────────────

    @Override
    protected void loadAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider reg) {
        super.loadAdditional(tag, reg);
        if (tag.contains("Items")) items.deserializeNBT(reg, tag.getCompound("Items"));
        if (tag.contains("Tank")) tank.readFromNBT(reg, tag.getCompound("Tank"));
        energy.stored = Math.max(0, tag.getInt("Energy"));
        progress = tag.getInt("Progress");
        int p = tag.contains("Piece") ? tag.getInt("Piece") : 1;
        selected = PIECES[Math.max(0, Math.min(PIECES.length - 1, p))];
        craftEnabled = !tag.contains("CraftEnabled") || tag.getBoolean("CraftEnabled");   // máquinas viejas: encendida
        lastTab = tag.getInt("LastTab");
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider reg) {
        super.saveAdditional(tag, reg);
        tag.put("Items", items.serializeNBT(reg));
        tag.put("Tank", tank.writeToNBT(reg, new CompoundTag()));
        tag.putInt("Energy", energy.stored);
        tag.putInt("Progress", progress);
        tag.putInt("Piece", pieceIndex(selected));
        tag.putBoolean("CraftEnabled", craftEnabled);
        tag.putInt("LastTab", lastTab);
    }

    public void dropContents(Level level, BlockPos pos) {
        for (int i = 0; i < items.getSlots(); i++) {
            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), items.getStackInSlot(i));
        }
    }

    @Override
    public @NotNull Component getDisplayName() {
        return Component.translatable("screen.zenkai.armor_fabricator.title");
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, @NotNull Inventory inv, @NotNull Player player) {
        return new ArmorFabricatorMenu(id, inv, this);
    }

    /** Solo para gametests: llena el búfer. */
    public void fillEnergyForTests(int fe) { energy.stored = Math.min(energy.capacity(), fe); }

    // ── Búfer de FE: solo entrada, gasto interno ─────────────────────────────

    public final class Energy implements IEnergyStorage {
        int stored;
        int capacity = ServerConfig.armorFabricatorFeCapacity();

        int capacity() { return capacity; }

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            int accepted = Math.min(capacity - stored, Math.min(ServerConfig.armorFabricatorMaxReceive(), maxReceive));
            if (accepted <= 0) return 0;
            if (!simulate) { stored += accepted; setChanged(); }
            return accepted;
        }

        @Override public int extractEnergy(int maxExtract, boolean simulate) { return 0; }
        @Override public int getEnergyStored() { return stored; }
        @Override public int getMaxEnergyStored() { return capacity; }
        @Override public boolean canExtract() { return false; }
        @Override public boolean canReceive() { return true; }
    }
}
