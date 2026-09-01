package com.hmc.zenkai.content.blockentity;

import com.hmc.zenkai.client.gui.menu.EnergyGeneratorMenu;
import com.hmc.zenkai.config.CommonConfig;
import com.hmc.zenkai.content.block.EnergyGeneratorBlock;
import com.hmc.zenkai.feature.generator.GeneratorFuel;
import com.hmc.zenkai.feature.generator.GeneratorFuels;
import com.hmc.zenkai.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Generador de FE: quema una cola de seis huecos y empuja la corriente a lo que tenga pegado.
energy_generator * CÓMO QUEMA (modelo Mekanism): el ítem se CONSUME de golpe al encenderlo y a partir de ahí
 * la máquina produce sus FE/tick durante los ticks que dure. No se "gasta poco a poco" en el
 * hueco. La consecuencia visible es que sacar la cola a mitad de quemado no recupera el ítem
 * que ya está ardiendo, igual que en un horno.
energy_generator * SIN CABLES A PROPÓSITO. Cada tick busca un IEnergyStorage en las seis caras y le mete lo
 * que acepte. Eso convierte "generador pegado al banco" en una máquina que funciona sola. Quien
 * tenga Mekanism puede enchufar un cable a cualquier cara y tirar de la capability normal: son
 * las dos vías de la misma energía saliendo del mismo búfer. Y EMPUJA en vez de esperar a que
 * le tiren porque el banco de scouter no sabe pedir, solo recibir.
energy_generator * EL CUBO VACÍO SE QUEDA EN SU HUECO. Es lo que hace vanilla con el horno y lo único que
 * permite automatizar: una tolva de salida puede sacarlo. Devolverlo al inventario del jugador
 * solo funcionaría si hay alguien delante, y una máquina alimentada por tolvas normalmente no
 * lo tiene.
 */
public class EnergyGeneratorBlockEntity extends BlockEntity implements MenuProvider {

    public static final int FUEL_SLOTS = 6;

    private final GeneratorEnergy energy = new GeneratorEnergy();

    /** Ticks que le quedan al ítem que arde, sus totales, y su potencia. Los tres van a NBT:
     *  el ítem ya se consumió, así que si no se guardan la máquina pierde el quemado en curso
     *  al recargar el chunk. */
    private int burnTicks;
    private int burnTicksTotal;
    private int burnFePerTick;

    private final ItemStackHandler fuel = new ItemStackHandler(FUEL_SLOTS) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }

        /** El hueco solo acepta combustible declarado en el datapack. Sin esto, un jugador
         *  puede meter su pico en la cola y bloquear el ciclo sin entender por qué. */
        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return GeneratorFuels.isFuel(stack);
        }
    };

    /**
     * Lo que ve la pantalla. UNA sola constante para el número de huecos, compartida con el
     * menú: si el cliente y el servidor discrepan en cuántos hay, el array se lee fuera de
     * rango y el crash aparece al abrir la GUI, no al compilar.
     */
    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> energy.get();
                case 1 -> energy.capacity();
                case 2 -> burnTicks;
                case 3 -> burnTicksTotal;
                case 4 -> burnFePerTick;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            // Nada que fijar desde el cliente: cada campo es de lectura. Dejarlo vacío
            // es la implementación correcta, no un pendiente.
        }

        @Override
        public int getCount() { return EnergyGeneratorMenu.DATA_SLOTS; }
    };

    public EnergyGeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ENERGY_GENERATOR.get(), pos, state);
    }

    public IEnergyStorage energyHandler() { return energy; }
    public GeneratorEnergy energyBuffer() { return energy; }
    public ItemStackHandler fuelHandler()  { return fuel; }
    public ContainerData containerData()   { return data; }
    public boolean isBurning() { return burnTicks > 0; }

    private final IItemHandler automation = new AutomationHandler();

    /** Lo que ven las tolvas y los conductos. Ver AutomationHandler. */
    public IItemHandler automationHandler() { return automation; }

    /**
     * Cara de automatización: deja METER combustible por cualquier lado y sacar SOLO lo que ya
     * no arde — el cubo vacío que dejó la lava.
    energy_generator     * El filtro de extracción no es un lujo: sin él, una tolva puesta debajo para recoger los
     * cubos se llevaría también el carbón recién insertado, y la máquina no arrancaría nunca.
     * Con él, la pareja "tolva arriba mete, tolva abajo saca" funciona sin más configuración,
     * que es exactamente lo que un jugador espera al ver seis huecos.
     */
    private final class AutomationHandler implements IItemHandler {

        @Override public int getSlots() { return FUEL_SLOTS; }

        @Override public @NotNull ItemStack getStackInSlot(int slot) {
            return fuel.getStackInSlot(slot);
        }

        @Override public int getSlotLimit(int slot) { return fuel.getSlotLimit(slot); }

        @Override public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return GeneratorFuels.isFuel(stack);
        }

        @Override public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack,
                                                       boolean simulate) {
            if (!GeneratorFuels.isFuel(stack)) return stack;
            return fuel.insertItem(slot, stack, simulate);
        }

        @Override public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
            // Solo sale lo que NO es combustible. Se comprueba contra el contenido real del
            // hueco y no contra lo que se pide, porque quien extrae no manda un ItemStack.
            if (GeneratorFuels.isFuel(fuel.getStackInSlot(slot))) return ItemStack.EMPTY;
            return fuel.extractItem(slot, amount, simulate);
        }
    }

    // ── Tick ─────────────────────────────────────────────────────────────────

    public static void serverTick(Level level, BlockPos pos, BlockState state,
                                  EnergyGeneratorBlockEntity be) {
        boolean wasBurning = be.isBurning();

        if (be.burnTicks > 0) {
            be.burnTicks--;
            be.energy.generate(be.burnFePerTick);
            if (be.burnTicks == 0) be.burnFePerTick = 0;
        }

        // Solo se enciende otro ítem si hace falta. Con el búfer lleno la cola se queda
        // intacta: quemar carbón para tirarlo es lo que un jugador no le perdona a una máquina.
        if (be.burnTicks <= 0 && !be.energy.isFull()) be.igniteNext();

        be.pushToNeighbours(level, pos);

        if (wasBurning != be.isBurning()) {
            level.setBlock(pos, state.setValue(EnergyGeneratorBlock.LIT, be.isBurning()), 3);
        }
        be.setChanged();
    }

    /**
     * Enciende el primer combustible de la cola, de izquierda a derecha y de arriba a abajo —
     * que es justo el orden de los índices del handler, así que el bucle ES el orden que ve
     * el jugador en pantalla.
     */
    private void igniteNext() {
        for (int i = 0; i < FUEL_SLOTS; i++) {
            ItemStack stack = fuel.getStackInSlot(i);
            if (stack.isEmpty()) continue;

            GeneratorFuel def = GeneratorFuels.of(stack);
            if (def == null) continue;   // dejó de ser combustible tras un /reload

            // El resto de la receta: el cubo de lava deja un cubo. Vanilla ya lo sabe, así
            // que no hace falta declararlo en el datapack de combustibles.
            ItemStack remainder = stack.getItem().getCraftingRemainingItem(stack);

            stack.shrink(1);
            if (stack.isEmpty()) fuel.setStackInSlot(i, ItemStack.EMPTY);

            if (!remainder.isEmpty()) placeRemainder(i, remainder);

            burnFePerTick  = def.fePerTick();
            burnTicks      = def.ticks();
            burnTicksTotal = def.ticks();
            setChanged();
            return;
        }
    }

    /**
     * Coloca el resto (el cubo vacío) en su propio hueco si quedó libre, y si no, en el
     * primero que haya. Si la cola está llena de combustible, se pierde: es el único caso en
     * el que no hay sitio, y frenar la máquina por un cubo sería peor que perderlo.
     */
    private void placeRemainder(int preferredSlot, ItemStack remainder) {
        if (fuel.getStackInSlot(preferredSlot).isEmpty()) {
            fuel.setStackInSlot(preferredSlot, remainder);
            return;
        }
        for (int i = 0; i < FUEL_SLOTS; i++) {
            if (fuel.getStackInSlot(i).isEmpty()) {
                fuel.setStackInSlot(i, remainder);
                return;
            }
        }
    }

    /** Reparte a las seis caras. Se para en cuanto el búfer se vacía. */
    private void pushToNeighbours(Level level, BlockPos pos) {
        if (energy.getEnergyStored() <= 0) return;

        for (Direction dir : Direction.values()) {
            if (energy.getEnergyStored() <= 0) return;

            // ⚠ API a verificar: getCapability(cap, pos, side) en NeoForge 1.21.1. El 'side'
            // es la cara POR LA QUE ENTRA al vecino, o sea la opuesta a la que sale de aquí.
            IEnergyStorage target = level.getCapability(
                    Capabilities.EnergyStorage.BLOCK, pos.relative(dir), dir.getOpposite());
            if (target == null || !target.canReceive()) continue;

            int offered = Math.min(CommonConfig.energyGeneratorMaxExtract(), energy.getEnergyStored());
            int accepted = target.receiveEnergy(offered, false);
            if (accepted > 0) energy.extractEnergy(accepted, false);
        }
    }

    // ── Menú ─────────────────────────────────────────────────────────────────

    @Override
    public @NotNull Component getDisplayName() {
        return Component.translatable("block.zenkai.energy_generator");
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, @NotNull Inventory inv,
                                                      @NotNull Player player) {
        return new EnergyGeneratorMenu(id, inv, this);
    }

    // ── NBT ──────────────────────────────────────────────────────────────────

    @Override
    protected void loadAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider reg) {
        super.loadAdditional(tag, reg);
        energy.load(tag);
        burnTicks      = tag.getInt("BurnTicks");
        burnTicksTotal = tag.getInt("BurnTicksTotal");
        burnFePerTick  = tag.getInt("BurnFePerTick");
        if (tag.contains("Fuel")) fuel.deserializeNBT(reg, tag.getCompound("Fuel"));
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider reg) {
        super.saveAdditional(tag, reg);
        energy.save(tag);
        tag.putInt("BurnTicks", burnTicks);
        tag.putInt("BurnTicksTotal", burnTicksTotal);
        tag.putInt("BurnFePerTick", burnFePerTick);
        tag.put("Fuel", fuel.serializeNBT(reg));
    }

    /** Suelta la cola al romper el bloque. Lo llama EnergyGeneratorBlock#onRemove.
     *  Vacía los huecos además de soltar: si el BE sobreviviera al onRemove (movido por un
     *  pistón, por ejemplo) los ítems estarían duplicados. */
    public void dropContents(Level level, BlockPos pos) {
        for (int i = 0; i < FUEL_SLOTS; i++) {
            ItemStack stack = fuel.getStackInSlot(i);
            if (!stack.isEmpty()) {
                net.minecraft.world.level.block.Block.popResource(level, pos, stack);
                fuel.setStackInSlot(i, ItemStack.EMPTY);
            }
        }
    }
}