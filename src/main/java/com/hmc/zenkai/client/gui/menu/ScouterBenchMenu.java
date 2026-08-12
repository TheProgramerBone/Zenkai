package com.hmc.zenkai.client.gui.menu;

import com.hmc.zenkai.client.gui.ModMenuTypes;
import com.hmc.zenkai.compat.CuriosCompat;
import com.hmc.zenkai.content.blockentity.PauseReason;
import com.hmc.zenkai.content.blockentity.ScouterBenchBlockEntity;
import com.hmc.zenkai.content.item.ScouterItem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * Menú del banco de scouter.
 *
 * CUATRO ZONAS, en este orden dentro de this.slots:
 *   0        banco      — el scouter que se está mejorando o reparando
 *   1        curio      — el hueco "scouter" de Curios
 *   2        casco      — el hueco de cabeza vanilla
 *   3..38    jugador    — inventario y hotbar
 *
 * Los dos huecos de equipado están porque el scouter se puede llevar en cualquiera de los dos
 * y el jugador elige: ScouterStacks.equipped ya mira curio primero y casco después. Tenerlos
 * aquí evita cerrar el banco, abrir el inventario, quitarse el scouter y volver.
 *
 * Los botones NO llevan paquete propio: van por clickMenuButton, que es red vanilla del menú
 * y ya trae la validación de "este jugador tiene este menú abierto".
 *   id 0..N-1 = mejora por ordinal de ScouterUpgrade
 *   id 99     = reparar
 *   id 100    = cancelar
 *
 * LAS COORDENADAS DE SLOT SON PARTE DEL ASSET: cada una tiene su pozo pintado en
 * scouter_bench.png, en (x-1, y-1) y de 18x18. Tocar un número de aquí obliga a repintar.
 */
public class ScouterBenchMenu extends AbstractContainerMenu {

    public static final int BTN_REPAIR = ScouterBenchBlockEntity.JOB_REPAIR;
    public static final int BTN_CANCEL = 100;

    /** Id del hueco de Curios donde vive el scouter. El mismo que lee ScouterStacks. */
    public static final String CURIOS_SLOT = "scouter";

    /** Índice del casco dentro de Inventory (36..39 son la armadura; 39 es la cabeza). */
    private static final int VANILLA_HELMET_INDEX = 39;

    // ── Layout de slots (debe coincidir con scouter_bench.png) ───────────────
    // Fila superior: tres pozos + el botón de tinte, con margen de 3 y huecos de 5. La
    // pantalla se estrechó a x=94 justo para que esta fila respirara — con la pantalla en 86
    // los cuatro elementos de 18 px no cabían y quedaban pegados unos a otros.
    private static final int BENCH_X = 4, BENCH_Y = 32;
    private static final int CURIOS_X = 27, CURIOS_Y = 32;
    private static final int HELMET_X = 50, HELMET_Y = 32;
    private static final int INV_X = 47, INV_Y = 172, HOTBAR_Y = 230;

    /** Primer y último+1 índice del inventario del jugador dentro de this.slots. */
    public static final int INV_FIRST = 3;
    public static final int SLOT_BENCH = 0;
    public static final int SLOT_CURIOS = 1;
    public static final int SLOT_HELMET = 2;
    public static final int INV_END = 39;



    private final Container container;
    private final ContainerData data;

    /** Huecos del ContainerData: progreso, duración, trabajo, motivo de pausa, energía,
     *  capacidad. Un único número para los dos lados, para que no se separen otra vez. */
    public static final int DATA_SLOTS = 6;

    /** Cliente: contenedor y datos ficticios; el servidor los rellena por sincronización.
     *  EL TAMAÑO TIENE QUE COINCIDIR con ScouterBenchBlockEntity.data.getCount(). Si el
     *  servidor manda más valores de los que hay aquí, setData revienta con
     *  IndexOutOfBounds al llegar el paquete y la pantalla se cae al renderizar. */
    public ScouterBenchMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(id, inv, new SimpleContainer(1), new SimpleContainerData(DATA_SLOTS));
    }

    public ScouterBenchMenu(int id, Inventory inv, Container container, ContainerData data) {
        super(ModMenuTypes.SCOUTER_BENCH.get(), id);
        checkContainerSize(container, 1);
        this.container = container;
        this.data = data;

        addSlot(new Slot(container, 0, BENCH_X, BENCH_Y) {
            @Override public boolean mayPlace(@NotNull ItemStack stack) {
                return stack.getItem() instanceof ScouterItem;
            }
            @Override public int getMaxStackSize() { return 1; }
        });

        addCuriosSlot(inv);

        // Casco vanilla. Un scouter ROTO también entra: la grieta se lleva puesta, y poder
        // verla desde el banco es justo el motivo de tener este hueco aquí.
        Slot helmet = new Slot(inv, VANILLA_HELMET_INDEX, HELMET_X, HELMET_Y) {
            @Override public boolean mayPlace(@NotNull ItemStack stack) {
                return stack.getItem() instanceof ScouterItem;
            }
            @Override public int getMaxStackSize() { return 1; }
        };
        // El yelmo gris de vanilla, el mismo sprite que el inventario del jugador. Vanilla lo
        // dibuja solo con el hueco vacío, que es justo lo que hace falta: un icono horneado
        // en el fondo asomaría por detrás del scouter puesto.
        // ⚠ Verificar en 1.21.1: Slot#setBackground(ResourceLocation atlas, ResourceLocation
        // sprite) e InventoryMenu.EMPTY_ARMOR_SLOT_HELMET.
        helmet.setBackground(InventoryMenu.BLOCK_ATLAS, InventoryMenu.EMPTY_ARMOR_SLOT_HELMET);
        addSlot(helmet);
        addPlayerInventory(inv);
        addDataSlots(data);
    }

    /**
     * Hueco de Curios. SIEMPRE se añade un slot, aunque no haya handler: el número de slots
     * tiene que ser idéntico en cliente y servidor o los índices de todo lo demás se
     * desplazan y el menú se desincroniza. Si no hay handler, el slot queda muerto
     * (mayPlace false) sobre un contenedor de usar y tirar.
     *
     * ⚠ API a verificar: ICurioStacksHandler.getStacks() debe seguir devolviendo un
     * IItemHandlerModifiable en la versión de Curios que uses.
     */
    private void addCuriosSlot(Inventory inv) {
        Optional<IItemHandlerModifiable> found = CuriosCompat.handler(inv.player, CURIOS_SLOT);
        IItemHandlerModifiable handler = found.orElseGet(() -> new ItemStackHandler(1));
        final boolean live = found.isPresent();

        addSlot(new SlotItemHandler(handler, 0, CURIOS_X, CURIOS_Y) {
            @Override public boolean mayPlace(@NotNull ItemStack stack) {
                return live && stack.getItem() instanceof ScouterItem;
            }
            @Override public int getMaxStackSize() { return 1; }
        });
    }

    private void addPlayerInventory(Inventory inv) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inv, col + row * 9 + 9, INV_X + col * 18, INV_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inv, col, INV_X + col * 18, HOTBAR_Y));
        }
    }

    public ItemStack scouter()  { return container.getItem(0); }
    /** Gasto de FE fuera del ciclo de trabajo. Solo servidor: en cliente el contenedor es
     *  ficticio y devuelve false, que es justo lo que debe pasar. */
    public boolean spendEnergy(int amount) {
        return container instanceof ScouterBenchBlockEntity be && be.spendEnergyNow(amount);
    }

    /** El scouter cambió sin pasar por un slot (lo tiñó el paquete de tinte). Hay que avisar
     *  o el modelo de la mesa se queda con el color anterior. */
    public void setScouterChanged() {
        container.setChanged();
        broadcastChanges();
    }
    public int progress()       { return data.get(0); }
    public int duration()       { return Math.max(1, data.get(1)); }
    public int job()            { return data.get(2); }
    public int energy()         { return data.get(4); }
    public int energyCapacity() { return Math.max(1, data.get(5)); }

    public PauseReason pauseReason() { return PauseReason.byId(data.get(3)); }
    public boolean isPaused()        { return pauseReason().isPaused(); }
    public boolean isWorking()       { return job() != ScouterBenchBlockEntity.JOB_NONE; }

    /** 0..1 para la barra de progreso. */
    public float progressFraction() {
        return isWorking() ? Math.min(1f, progress() / (float) duration()) : 0f;
    }

    /** 0..1 para la barra de energía. */
    public float energyFraction() {
        return Math.min(1f, energy() / (float) energyCapacity());
    }

    /** Segundos que faltan, redondeando hacia arriba. 0 si no hay trabajo. */
    public int secondsLeft() {
        if (!isWorking()) return 0;
        int ticks = Math.max(0, duration() - progress());
        return (ticks + 19) / 20;
    }

    @Override
    public boolean clickMenuButton(@NotNull Player player, int id) {
        if (!(player instanceof ServerPlayer sp)) return false;
        if (!(container instanceof ScouterBenchBlockEntity be)) return false;

        if (id == BTN_CANCEL) {
            be.cancelJob();
            return true;
        }
        return be.startJob(sp, id);
    }

    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;

        ItemStack raw = slot.getItem();
        ItemStack copy = raw.copy();

        if (index < INV_FIRST) {
            // Banco, curio o casco -> jugador.
            if (!moveItemStackTo(raw, INV_FIRST, slots.size(), true)) return ItemStack.EMPTY;
        } else if (raw.getItem() instanceof ScouterItem) {
            // Del inventario: primero al banco, y si está ocupado a los huecos de equipado.
            // El orden importa — quien abre el banco viene a mejorar, no a vestirse.
            if (!moveItemStackTo(raw, 0, INV_FIRST, false)) return ItemStack.EMPTY;
        } else {
            return ItemStack.EMPTY; // los materiales se quedan en el inventario, no se meten
        }

        if (raw.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        return copy;
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        return container.stillValid(player);
    }
}