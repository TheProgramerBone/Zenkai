package com.hmc.zenkai.content.blockentity;

import com.hmc.zenkai.client.gui.menu.ScouterBenchMenu;
import com.hmc.zenkai.content.block.ScouterBenchBlock;
import com.hmc.zenkai.content.item.ScouterItem;
import com.hmc.zenkai.feature.sense.*;
import com.hmc.zenkai.registry.ModBlockEntities;
import com.hmc.zenkai.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.*;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.UUID;

/**
 * Block entity del banco de scouter. Un slot (el scouter) y un trabajo en curso.
 * REGLA ÚNICA DEL TRABAJO: avanza solo si el jugador que lo inició sigue conectado Y sigue
 * teniendo los materiales. Si falla cualquiera de las dos, se PAUSA sin perder progreso.
 * Es la única forma de que convivan las tres reglas de diseño: el trabajo sobrevive a cerrar
 * la GUI, los materiales se cobran AL TERMINAR (no reservados: cancelar no debe costar nada)
 * y no existe el trabajo zombi que llega al 100% sin nadie a quien cobrarle.
 * DOS NIVELES DE "ALGO CAMBIÓ", y no son intercambiables:
 *  - markDirty(): solo marca el chunk para guardarse. Es lo que usa el progreso, que corre
 *    cada tick.
 *  - sync(): además manda el block entity ENTERO al que tenga el chunk cargado. Solo
 *    para lo que el cliente necesita ver sin la GUI abierta: qué scouter hay en la mesa.
 * Sincronizar el progreso por aquí sería un paquete con un ItemStack dentro 20 veces por
 * segundo durante el trabajo; para eso está ContainerData, que solo llega a quien tiene
 * la pantalla abierta.
 */
public class ScouterBenchBlockEntity extends BaseContainerBlockEntity implements GeoBlockEntity {

    /** Fijo para cada una de las mejoras y para la reparación. */
    public static final int WORK_TICKS = 100; // 5 s

    /** Valores de `job` que no son una mejora. */
    public static final int JOB_NONE   = -1;
    public static final int JOB_REPAIR = 99;

    private static final RawAnimation WORKING = RawAnimation.begin().thenLoop("working");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private final NonNullList<ItemStack> items = NonNullList.withSize(1, ItemStack.EMPTY);

    /**
     * Puente al lado cliente. El BE es código común y no puede referenciar SoundInstance ni
     * Minecraft: en un servidor dedicado esas clases no existen. El cliente rellena este
     * hook en su setup y el servidor lo deja vacío.
     */
    public static java.util.function.Consumer<ScouterBenchBlockEntity> clientTickHook = be -> {};

    /** Ticker de cliente. Solo lleva el sonido; la lógica del trabajo es del servidor. */
    public static void clientTick(Level level, BlockPos pos, BlockState state,
                                  ScouterBenchBlockEntity be) {
        clientTickHook.accept(be);
    }

    private int job = JOB_NONE;
    private int progress = 0;
    private PauseReason pause = PauseReason.NONE;

    private final BenchEnergy energy = new BenchEnergy();
    /** FE ya cobrada del trabajo en curso. Con el progreso decide cuánto toca este tick. */
    private int energySpent = 0;

    /** Para distinguir "metieron scouter" de "lo sacaron" cuando cambia el slot. */
    private boolean hadScouter = false;

    @Nullable private UUID owner = null;

    /** Lo que ve la GUI. Índices: 0 progreso, 1 duración, 2 trabajo, 3 motivo de pausa,
     *  4 energía, 5 capacidad. */
    private final ContainerData data = new ContainerData() {
        @Override public int get(int i) {
            return switch (i) {
                case 0 -> progress;
                case 1 -> WORK_TICKS;
                case 2 -> job;
                case 3 -> pause.ordinal();
                case 4 -> energy.get();
                case 5 -> energy.capacity();
                default -> 0;
            };
        }
        @Override public void set(int i, int v) { /* solo lectura desde el cliente */ }
        @Override public int getCount() { return ScouterBenchMenu.DATA_SLOTS; }
    };

    public ScouterBenchBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SCOUTER_BENCH.get(), pos, state);
    }

    // ── Contenedor ───────────────────────────────────────────────────────────

    @Override protected @NotNull NonNullList<ItemStack> getItems() { return items; }

    @Override public int getContainerSize() { return 1; }

    /**
     * Lo llama vanilla al colocar el banco desde un ítem que traía contenido (componente
     * container). Dejarlo vacío se traga el scouter que hubiera dentro.
     */
    @Override
    protected void setItems(@NotNull NonNullList<ItemStack> incoming) {
        for (int i = 0; i < items.size(); i++) {
            items.set(i, i < incoming.size() ? incoming.get(i) : ItemStack.EMPTY);
        }
    }

    @Override
    public boolean canPlaceItem(int slot, @NotNull ItemStack stack) {
        return stack.getItem() instanceof ScouterItem;
    }

    /** El scouter que hay dentro, o EMPTY. */
    public ItemStack scouter() { return items.getFirst(); }

    // ── Reserva de uso ───────────────────────────────────────────────────────

    /** Un solo jugador a la vez MIENTRAS haya trabajo. Con el banco parado, entra cualquiera. */
    public boolean canBeUsedBy(Player player) {
        return job == JOB_NONE || owner == null || owner.equals(player.getUUID());
    }

    public void claim(Player player) {
        if (job == JOB_NONE || owner == null) owner = player.getUUID();
    }

    /** Cierra también la vía estándar de apertura, no solo la del bloque. */
    @Override
    public boolean canOpen(@NotNull Player player) {
        return canBeUsedBy(player) && super.canOpen(player);
    }

    // ── Trabajo ──────────────────────────────────────────────────────────────

    public int job()          { return job; }
    public int progress()     { return progress; }
    public PauseReason pauseReason() { return pause; }
    public boolean isPaused()        { return pause.isPaused(); }

    /** Para el capability. El banco solo recibe: nadie puede vaciarlo desde fuera. */
    public BenchEnergy energyHandler() { return energy; }

    /**
     * Gasto puntual de FE, fuera del ciclo de trabajo. Lo usa el tinte, que es instantáneo:
     * pasar por un trabajo de 100 ticks para algo cosmético bloquearía el banco cinco
     * segundos por cambiar de color.
     * Devuelve false si no hay suficiente, y entonces no se cobra nada más.
     */
    public boolean spendEnergyNow(int amount) {
        if (!energy.spend(amount)) return false;
        markDirty();
        return true;
    }

    /**
     * Arranca un trabajo. Devuelve false si no se puede (ya hay uno, no hay scouter, la mejora
     * está al máximo o el scouter está roto y esto no es una reparación).
     * NO cobra nada: el cobro es al terminar.
     */
    public boolean startJob(ServerPlayer sp, int newJob) {
        if (job != JOB_NONE) return false;
        ItemStack s = scouter();
        if (s.isEmpty()) return false;

        if (newJob == JOB_REPAIR) {
            if (!ScouterStacks.isBroken(s)){
                playAt(ModSounds.SCOUTER_BENCH_FAIL.get(), 0.7f);
                return false;}
        } else {
            // Un scouter reventado no se mejora: primero se arregla. Mejorar un aparato roto
            // dejaría al jugador pagando por algo que no puede ni encender.
            if (ScouterStacks.isBroken(s)) {
                playAt(ModSounds.SCOUTER_BENCH_FAIL.get(), 0.7f);
                return false;
            }
            ScouterUpgrade u = upgradeOf(newJob);
            if (u == null || ScouterStacks.upgrades(s).nextLevel(u) < 0) {
                playAt(ModSounds.SCOUTER_BENCH_FAIL.get(), 0.7f);
                return false;
            }
        }

        job = newJob;
        progress = 0;
        energySpent = 0;
        // Arranca aunque el búfer esté vacío: el primer tick lo dejará en PAUSE_ENERGY con
        // progreso 0 y seguirá esperando corriente. Una máquina sin alimentar se para, no
        // rechaza el trabajo — y los materiales tampoco se reservan al empezar.
        pause = PauseReason.NONE;
        owner = sp.getUUID();
        setWorkingState(true);
        sync();
        playAt(ModSounds.SCOUTER_BENCH_START.get(), 0.7f);
        return true;
    }

    /** Cancela sin cobrar ni devolver nada: nunca se retiró material, y la FE gastada hasta
     *  aquí se pierde, que es lo que pasa con la corriente de una máquina que se apaga. */
    public void cancelJob() {
        if (job == JOB_NONE) return;
        job = JOB_NONE;
        progress = 0;
        energySpent = 0;
        pause = PauseReason.NONE;
        setWorkingState(false);
        playAt(ModSounds.SCOUTER_BENCH_CANCEL.get(), 0.6f);
        sync();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state,
                                  ScouterBenchBlockEntity be) {
        if (be.job == JOB_NONE) return;

        // Sacar el scouter cancela el trabajo entero, sin coste.
        if (be.scouter().isEmpty()) {
            be.cancelJob();
            return;
        }

        ServerPlayer sp = be.ownerPlayer(level);
        ScouterUpgradeCost cost = be.currentCost();

        // Orden de comprobación = orden de gravedad. Sin dueño no se puede ni mirar el
        // inventario, así que va primero; la energía la última porque es la que se arregla
        // sola en cuanto llega corriente.
        PauseReason reason = PauseReason.NONE;
        int due = 0;
        if (sp == null) {
            reason = PauseReason.OWNER;
        } else if (!cost.canAfford(sp.getInventory())) {
            reason = PauseReason.MATERIALS;
        } else {
            // Cuánta FE DEBERÍA llevar gastada al terminar este tick. Restando lo ya gastado
            // sale lo que toca ahora, y al llegar a WORK_TICKS el objetivo es exactamente el
            // total del JSON: se cobra 20.001 y no 20.000 ni 20.002, sin acumulador aparte.
            long total = cost.energy();
            int target = (int) (total * (be.progress + 1) / WORK_TICKS);
            due = target - be.energySpent;
            if (!be.energy.spend(due)) reason = PauseReason.ENERGY;
        }

        if (be.pause != reason) {
            be.pause = reason;
            // sync() y no markDirty(): el cliente necesita enterarse de la pausa para callar
            // el bucle de sonido. Pasa pocas veces —cuando falta corriente o material—, así
            // que el paquete de bloque no es un coste real.
            be.sync();
        }
        if (reason.isPaused()) return;

        be.energySpent += due;
        be.progress++;
        if (be.progress < WORK_TICKS) {
            be.markDirty();   // guardar sí, difundir no: el progreso va por ContainerData
            return;
        }

        // Terminado: cobrar materiales y aplicar, en ese orden.
        assert sp != null;
        cost.consume(sp.getInventory());
        be.applyJob();
        be.job = JOB_NONE;
        be.progress = 0;
        be.energySpent = 0;
        be.setWorkingState(false);
        be.playAt(ModSounds.SCOUTER_BENCH_FINISH.get(), 0.8f);
        be.sync();
    }

    private void applyJob() {
        ItemStack s = scouter();
        if (s.isEmpty()) return;

        if (job == JOB_REPAIR) {
            ScouterStacks.repair(s);
            return;
        }
        ScouterUpgrade u = upgradeOf(job);
        if (u == null) return;
        ScouterUpgrades up = ScouterStacks.upgrades(s);
        int next = up.nextLevel(u);
        if (next > 0) ScouterStacks.setUpgrades(s, up.with(u, next));
    }

    /** Coste del trabajo en curso. */
    public ScouterUpgradeCost currentCost() {
        if (job == JOB_REPAIR) return ScouterRepair.benchCost();
        ScouterUpgrade u = upgradeOf(job);
        if (u == null) return ScouterUpgradeCost.FREE;
        int next = ScouterStacks.upgrades(scouter()).nextLevel(u);
        return next < 0 ? ScouterUpgradeCost.FREE : ScouterUpgradeCost.forLevel(u, next);
    }

    @Nullable
    private static ScouterUpgrade upgradeOf(int ordinal) {
        ScouterUpgrade[] v = ScouterUpgrade.values();
        return (ordinal >= 0 && ordinal < v.length) ? v[ordinal] : null;
    }

    @Nullable
    private ServerPlayer ownerPlayer(Level level) {
        if (owner == null || level.getServer() == null) return null;
        return level.getServer().getPlayerList().getPlayer(owner);
    }

    private void setWorkingState(boolean working) {
        if (level == null) return;
        BlockState st = getBlockState();
        if (st.hasProperty(ScouterBenchBlock.WORKING)
                && st.getValue(ScouterBenchBlock.WORKING) != working) {
            level.setBlock(worldPosition, st.setValue(ScouterBenchBlock.WORKING, working), 3);
        }
    }

    // ── Menú ─────────────────────────────────────────────────────────────────

    @Override
    protected @NotNull Component getDefaultName() {
        return Component.translatable("block.zenkai.scouter_bench");
    }

    @Override
    protected @NotNull AbstractContainerMenu createMenu(int id, @NotNull Inventory inv) {
        return new ScouterBenchMenu(id, inv, this, data);
    }

    // ── Persistencia y sincronización ────────────────────────────────────────

    @Override
    protected void loadAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider reg) {
        super.loadAdditional(tag, reg);
        items.set(0, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, items, reg);
        job = tag.contains("Job") ? tag.getInt("Job") : JOB_NONE;
        progress = tag.getInt("Progress");
        pause = PauseReason.byId(tag.getInt("Pause"));
        energySpent = tag.getInt("EnergySpent");
        energy.load(tag);
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        // Arranca con el estado REAL del slot. Sin esto, cargar una partida con el scouter
        // dentro haría sonar "insert" en el primer setChanged, porque hadScouter estaría en
        // false y el banco creería que acaban de meterlo.
        hadScouter = !scouter().isEmpty();
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider reg) {
        super.saveAdditional(tag, reg);
        ContainerHelper.saveAllItems(tag, items, reg);
        tag.putInt("Job", job);
        tag.putInt("Progress", progress);
        tag.putInt("Pause", pause.ordinal());
        tag.putInt("EnergySpent", energySpent);
        energy.save(tag);
        if (owner != null) tag.putUUID("Owner", owner);
    }

    /** El cliente necesita el ItemStack del slot para RENDERIZAR el scouter sobre el bloque
     *  aunque no haya nadie con la GUI abierta. */
    @Override
    public @NotNull CompoundTag getUpdateTag(HolderLookup.@NotNull Provider reg) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, reg);
        return tag;
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** Solo marca para guardar. Sin difusión: lo llama el progreso, que corre cada tick. */
    private void markDirty() {
        if (level != null) level.blockEntityChanged(worldPosition);
    }

    /** Un solo sitio para reproducir sonidos del banco. Categoría BLOCKS y posición del
     *  bloque: el banco es la fuente, no el jugador. */
    private void playAt(SoundEvent sound, float volume) {
        if (level == null || level.isClientSide) return;
        level.playSound(null, worldPosition, sound, SoundSource.BLOCKS, volume, 1.0f);
    }

    /** Suena al meter o sacar el aparato. Se compara contra el estado anterior porque
     *  setChanged no dice QUÉ cambió, solo que algo cambió. */
    private void checkScouterSound() {
        if (level == null || level.isClientSide) return;
        boolean now = !scouter().isEmpty();
        if (now != hadScouter) {
            hadScouter = now;
            playAt(now ? ModSounds.SCOUTER_BENCH_INSERT.get()
                    : ModSounds.SCOUTER_BENCH_REMOVE.get(), 0.6f);
        }
    }

    /**
     * Guarda Y difunde. Hoy es un alias de setChanged(), que ya hace las dos cosas — se
     * mantiene como manera aparte porque nombra la INTENCIÓN en los sitios donde el cliente
     * tiene que enterarse sí o sí: el scouter de la mesa, el fin del trabajo y el motivo de
     * pausa (que apaga el bucle de sonido).
     * Antes difundía por su cuenta ADEMÁS de lo que hace setChanged, y salían dos paquetes
     * de bloque por cada cambio.
     */
    private void sync() {
        setChanged();
    }

    /**
     * Vanilla lo llama cuando cambia el contenido del slot (meter o sacar el scouter), y eso
     * SÍ hay que difundirlo o el modelo de la mesa se queda con el aparato anterior.
     */
    @Override
    public void setChanged() {
        super.setChanged();
        if (level != null && !level.isClientSide) {
            checkScouterSound();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    // ── GeckoLib ─────────────────────────────────────────────────────────────

    /**
     * Un solo controlador y una sola animación. Antes caía en un RawAnimation "idle" que NO
     * existe en scouter_bench.animation.json: el banco está parado casi siempre, así
     * que GeckoLib reclamaba una animación ausente en cada tick de render. Parado = STOP, que
     * es lo que significa de verdad "sin animación".
     */
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "controller", 5, state -> {
            boolean working = getBlockState().hasProperty(ScouterBenchBlock.WORKING)
                    && getBlockState().getValue(ScouterBenchBlock.WORKING);
            return working ? state.setAndContinue(WORKING) : PlayState.STOP;
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }

    @Override
    public double getTick(Object object) {
        return this.level != null ? this.level.getGameTime() : 0;
    }
}