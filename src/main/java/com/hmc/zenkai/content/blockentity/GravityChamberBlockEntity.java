package com.hmc.zenkai.content.blockentity;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.gui.menu.GravityChamberMenu;
import com.hmc.zenkai.config.ServerConfig;
import com.hmc.zenkai.content.block.GravityChamberBlock;
import com.hmc.zenkai.content.item.GravityUpgradeItem;
import com.hmc.zenkai.content.item.TrainingDroneItem;
import com.hmc.zenkai.feature.gravity.GravityDroneRound;
import com.hmc.zenkai.feature.combat.ZenkaiStats;
import com.hmc.zenkai.feature.gravity.GravityFields;
import com.hmc.zenkai.feature.weights.WeightSystem;
import com.hmc.zenkai.registry.ModBlockEntities;
import com.hmc.zenkai.registry.ModBlocks;
import com.hmc.zenkai.registry.ModGameRules;
import com.hmc.zenkai.registry.ModTags;
import com.hmc.zenkai.util.ItemHandlers;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;

/**
 * Consola de la cámara de gravedad (Fase 3 de la 0.7.0; diseño en pendientes-0.7.0.md §3.3 y
 * docs/pesas-y-gravedad.md).
 *
 * SALA: esfera de radio R (3–10, se elige en la pantalla) con la consola en la capa más baja del
 * interior: centro = consola + (0, R−1, 0). La sala vale si el aire alrededor de la consola está
 * CERRADO dentro de esa esfera; la pared puede ser de cualquier bloque con colisión. Una sala más
 * pequeña que la esfera vale igual; lo que se escapa de la esfera es una FUGA.
 *
 * GRAVEDAD: la mejora de la ranura fija la g máxima; la g real la elige el jugador. Gasta
 * {@code 2·(g−1)·(1 + 0,25·(R−5))} FE/t; sin FE la g vuelve a 1 al instante. Las toneladas salen de
 * {@link WeightSystem#tonsForGravity}: el único dueño de las matemáticas sigue siendo WeightSystem.
 *
 * QUIÉN LA SUFRE: jugadores (vía {@link GravityFields}: la carga, la lentitud, el TP y el drenaje los
 * aplica WeightLoadSystem como con cualquier gravedad) y mobs que no estén en {@code zenkai:gravity_immune}
 * (aquí mismo: modificadores transitorios propios y drenaje con suelo en 1). El PL de un mob sale de
 * {@link ZenkaiStats#resolveDisplayPowerLevel}, que cubre mobs sin stats propias.
 *
 * DRONES (Fase 4): ocho ranuras de {@link TrainingDroneItem}; el botón «Iniciar ronda» genera hasta
 * {@link #maxDrones()} entidades y la ronda entera vive en {@link GravityDroneRound}. Las ranuras se
 * bloquean mientras dura (entrar y salir), para que nadie esquive el gasto de usos sacando el ítem.
 *
 * RENDIMIENTO (requisito del usuario): el relleno se hace cada 100 ticks escalonado por posición, con
 * colecciones de fastutil reutilizadas y el LevelChunk en caché (unas 3 600 celdas con R = 10, sin crear
 * objetos por celda) y sin cargar chunks; las entidades se repasan cada 5 ticks; los modificadores solo se
 * reescriben si cambian; al cliente solo viaja lo visible y solo cuando cambia.
 */
public class GravityChamberBlockEntity extends BlockEntity implements MenuProvider {

    /** Radio de la esfera: de 3 a 10 (pedido del usuario, 2026-11-20: mínimo 3; antes 5–10 y antes 3–8). */
    public static final int MIN_RADIUS = 3, MAX_RADIUS = 10, DEFAULT_RADIUS = 7;
    /** Radio al que está calibrado el coste de FE (el factor del radio vale 1 aquí; con 3 sale más barato). */
    private static final int COST_BASE_RADIUS = 5;
    public static final int MIN_INTERIOR = 27;
    /** Tope duro del relleno: más que el interior de una esfera de radio 10 (unas 3 600 celdas). */
    private static final int MAX_INTERIOR = 4_800;
    public static final int MAX_LEAKS = 64;
    private static final int VALIDATE_INTERVAL = 100, ENTITY_INTERVAL = 5;

    // Estado legible (ContainerData y pantalla). Orden = prioridad del aviso.
    public static final int STATUS_ACTIVE = 0, STATUS_IDLE = 1, STATUS_OPEN = 2, STATUS_TOO_SMALL = 3,
            STATUS_NO_UPGRADE = 4, STATUS_NO_ENERGY = 5, STATUS_OFF = 6;

    private static final ResourceLocation MOVE_MOD = ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "gravity_move");
    private static final ResourceLocation JUMP_MOD = ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "gravity_jump");

    /** Consolas cargadas en el CLIENTE, para el holograma (GravityHologramRenderer). Sin clases de cliente aquí. */
    public static final Set<GravityChamberBlockEntity> CLIENT_LOADED = Collections.newSetFromMap(new java.util.WeakHashMap<>());

    private final Energy energy = new Energy();
    private final ItemStackHandler upgrade = new ItemStackHandler(1) {
        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) { return stack.getItem() instanceof GravityUpgradeItem; }

        @Override
        public int getSlotLimit(int slot) { return 1; }

        @Override
        protected void onContentsChanged(int slot) { setChanged(); }
    };

    /** Ranuras de drones (Fase 4). Bloqueadas durante una ronda, también para la automatización. */
    private final ItemStackHandler drones = new ItemStackHandler(GravityDroneRound.MAX_DRONES) {
        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return stack.getItem() instanceof TrainingDroneItem && !droneRound.running();
        }

        @Override
        public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
            return droneRound.running() ? ItemStack.EMPTY : super.extractItem(slot, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) { return 1; }

        @Override
        protected void onContentsChanged(int slot) { setChanged(); }
    };
    private final GravityDroneRound droneRound = new GravityDroneRound(this);
    /** Modo de la próxima ronda: false = pasivo (blancos), true = agresivo (disparan). Guardado. */
    private boolean aggressiveMode;
    /** Dificultad de la próxima ronda (Fase 4, tabla en DroneDifficulty). Guardada. */
    private com.hmc.zenkai.feature.gravity.DroneDifficulty droneDifficulty = com.hmc.zenkai.feature.gravity.DroneDifficulty.NORMAL;

    private int targetG = 10;
    private int radius = DEFAULT_RADIUS;
    private boolean hologram;
    /** Interruptor de la gravedad (botón de la pantalla): apagada no genera ni gasta, la sala se sigue validando. */
    private boolean enabled = true;
    /** Última vista de la pantalla (true = Drones). Guardada: la consola reabre en la vista en que se dejó. */
    private boolean droneView;

    // Resultado de la validación (servidor) y lo que de él se sincroniza.
    private boolean formed;
    private int interiorCount;
    private final LongOpenHashSet interior = new LongOpenHashSet();
    private final LongArrayList leaks = new LongArrayList();
    private final LongArrayList ports = new LongArrayList();
    private boolean needsValidate = true;

    private boolean active;
    private int lastCost;
    /** Mobs con nuestros modificadores puestos, para quitárselos al salir o al apagarse. */
    private final Set<UUID> affectedMobs = new HashSet<>();

    // Reutilizados por el relleno (solo hilo del servidor).
    private final LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
    private final LongOpenHashSet seenWalls = new LongOpenHashSet();
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

    // Holograma (cliente): corteza en caché por radio y lo que falta, recalculado cada 10 ticks.
    private long[] shellCache = new long[0];
    private int shellRadius = -1;
    private long[] missingCache = new long[0];
    private long missingAt = Long.MIN_VALUE;

    public GravityChamberBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.GRAVITY_CHAMBER.get(), pos, state);
    }

    // ── Lectura ──────────────────────────────────────────────────────────────

    public IEnergyStorage energyHandler() { return energy; }
    public ItemStackHandler upgradeHandler() { return upgrade; }
    public ItemStackHandler droneHandler() { return drones; }
    public GravityDroneRound droneRound() { return droneRound; }
    public boolean aggressiveMode() { return aggressiveMode; }
    public com.hmc.zenkai.feature.gravity.DroneDifficulty droneDifficulty() { return droneDifficulty; }

    /** Pasa a la siguiente dificultad (no durante una ronda). */
    public void cycleDroneDifficulty() {
        if (droneRound.running()) return;
        droneDifficulty = droneDifficulty.next();
        setChanged();
    }
    /** Celdas del interior validado (solo servidor; vacío sin sala). Para la ronda de drones. */
    public long[] interiorCells() { return interior.toLongArray(); }
    public boolean interiorContainsKey(long key) { return interior.contains(key); }

    /** Escribe una ranura de drones saltándose el bloqueo de la ronda (solo la ronda gasta usos así). */
    public void setDroneDirect(int slot, ItemStack stack) { drones.setStackInSlot(slot, stack); }
    public boolean isFormed() { return formed; }
    public boolean isActive() { return active; }
    public int radius() { return radius; }
    public boolean hologramOn() { return hologram; }
    public int interiorCount() { return interiorCount; }
    public long[] leaks() { return leaks.toLongArray(); }
    public boolean isPortLinked(BlockPos p) { return ports.contains(p.asLong()); }
    public int portCount() { return ports.size(); }

    /** g máxima de la mejora puesta; 1 sin mejora (no genera gravedad). */
    public int maxGravity() { return GravityUpgradeItem.maxGravityOf(upgrade.getStackInSlot(0)); }

    /** g que se aplicaría ahora (la elegida, recortada al máximo de la mejora). */
    public int effectiveGravity() { return Math.max(1, Math.min(targetG, maxGravity())); }
    public int targetGravity() { return targetG; }

    /** g que está generando de verdad (1 si está apagada). */
    public double activeGravity() { return active ? effectiveGravity() : 1.0; }

    /** Drones a la vez (Fase 4): cuanto mayor la esfera, más drones (decisión del usuario, 2026-11-19). */
    public int maxDrones() { return radius - 2; }   // radio 3 → 1 dron, radio 5 → 3, radio 10 → 8 (el máximo del diseño)

    public boolean isEnabled() { return enabled; }

    public void toggleEnabled() {
        enabled = !enabled;
        setChanged();
    }

    /** FE por tick de una g y un radio: {@code 2·(g−1)·(1 + 0,25·(R−5))}, redondeado hacia arriba (con R = 3, ×0,5). */
    public static int costPerTick(int g, int radius) {
        if (g <= 1) return 0;
        double c = ServerConfig.gravityChamberFePerG() * (g - 1)
                * (1.0 + ServerConfig.gravityChamberFeRadiusStep() * (radius - COST_BASE_RADIUS));
        return (int) Math.ceil(c);
    }

    public int status() {
        if (!formed) return interiorCount > 0 && leaks.isEmpty() ? STATUS_TOO_SMALL : STATUS_OPEN;
        if (maxGravity() <= 1) return STATUS_NO_UPGRADE;
        if (!enabled) return STATUS_OFF;
        if (effectiveGravity() <= 1) return STATUS_IDLE;
        return active ? STATUS_ACTIVE : STATUS_NO_ENERGY;
    }

    /** Centro de la esfera para un radio: la consola está en la capa más baja del interior. */
    public static BlockPos sphereCenter(BlockPos console, int radius) { return console.above(radius - 1); }

    /** ¿La celda (offset entero desde el centro) está dentro de la esfera? d ≤ R − 0,5. */
    public static boolean insideSphere(int dx, int dy, int dz, int radius) {
        double lim = radius - 0.5;
        return dx * dx + dy * dy + dz * dz <= lim * lim;
    }

    // ── Botones (clickMenuButton, servidor) ──────────────────────────────────

    public void addTargetGravity(int delta) {
        // Sin mejora se deja elegir hasta ×500 para dejarla preparada; con mejora, hasta su máximo.
        int cap = maxGravity() > 1 ? maxGravity() : 500;
        int next = Math.max(1, Math.min(cap, targetG + delta));
        if (next == targetG) return;
        targetG = next;
        setChanged();
    }

    public void addRadius(int delta) {
        int next = Math.max(MIN_RADIUS, Math.min(MAX_RADIUS, radius + delta));
        if (next == radius) return;
        radius = next;
        setChanged();
        revalidateNow();
        syncToClient();
    }

    /** Cambia el modo de la próxima ronda (no durante una ronda). */
    public void toggleDroneMode() {
        if (droneRound.running()) return;
        aggressiveMode = !aggressiveMode;
        setChanged();
    }

    /** Botón de la ronda: la empieza o, si ya hay una, la cancela (solo su dueño). */
    public void pressRound(Player player) {
        if (!(level instanceof ServerLevel sl) || !(player instanceof net.minecraft.server.level.ServerPlayer sp)) return;
        if (droneRound.running()) {
            if (sp.getUUID().equals(droneRound.owner())) droneRound.cancel(sl);
            return;
        }
        String err = droneRound.start(sl, sp, aggressiveMode, droneDifficulty);
        if (err != null) sp.displayClientMessage(Component.translatable(err).withStyle(net.minecraft.ChatFormatting.RED), true);
    }

    public void toggleHologram() {
        hologram = !hologram;
        setChanged();
        revalidateNow();
        syncToClient();
    }

    /** Pide revalidar en el próximo tick (al abrir la pantalla, botones). */
    public void revalidateNow() { needsValidate = true; }

    // ── Tick ─────────────────────────────────────────────────────────────────

    public static void serverTick(Level level, BlockPos pos, BlockState state, GravityChamberBlockEntity be) {
        long now = level.getGameTime();
        // Escalonado por posición: varias consolas no validan en el mismo tick.
        if (be.needsValidate || (now + (pos.asLong() & 0x7FFFFFFF)) % VALIDATE_INTERVAL == 0) {
            be.needsValidate = false;
            be.validate();
        }

        boolean wants = be.enabled && be.formed && be.effectiveGravity() > 1;
        int cost = wants ? costPerTick(be.effectiveGravity(), be.radius) : 0;
        boolean nowActive = wants && be.energy.spend(cost);
        be.lastCost = nowActive ? cost : 0;
        if (nowActive != be.active) {
            be.active = nowActive;
            if (state.getValue(GravityChamberBlock.ACTIVE) != nowActive) {
                level.setBlock(pos, state.setValue(GravityChamberBlock.ACTIVE, nowActive), 3);
            }
            if (!nowActive) be.releaseMobs();
            be.syncToClient();
        }
        if (nowActive) be.setChanged();

        if (nowActive && now % ENTITY_INTERVAL == 0 && level instanceof ServerLevel sl) be.tickEntities(sl, now);
        // La ronda se repasa aunque la cámara se haya apagado: así ve la falta de energía y la cancela.
        if (be.droneRound.running() && now % GravityDroneRound.TICK_INTERVAL == 0 && level instanceof ServerLevel sl) {
            be.droneRound.tick(sl, now);
        }
    }

    private void tickEntities(ServerLevel level, long now) {
        double g = effectiveGravity();
        double tons = WeightSystem.tonsForGravity(g);
        BlockPos c = sphereCenter(worldPosition, radius);
        AABB box = new AABB(c).inflate(radius);
        boolean damage = ModGameRules.gravityDamage(level.getServer());
        Set<UUID> stillInside = new HashSet<>();

        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box,
                le -> le.isAlive() && interior.contains(le.blockPosition().asLong()))) {
            if (e instanceof Player p) {
                if (!p.isSpectator()) GravityFields.refresh(p, g, now);
                continue;
            }
            if (!(e instanceof Mob mob) || mob.getType().is(ModTags.EntityTypes.GRAVITY_IMMUNE)) continue;
            long pl = ZenkaiStats.resolveDisplayPowerLevel(mob);
            double load = WeightSystem.loadFor(pl, tons);
            double jumpLoad = WeightSystem.loadFor(pl, tons * ServerConfig.weightGravityJumpFactor());
            setMod(mob, Attributes.MOVEMENT_SPEED, MOVE_MOD, WeightSystem.moveFactor(load));
            setMod(mob, Attributes.FLYING_SPEED, MOVE_MOD, WeightSystem.moveFactor(load));
            setMod(mob, Attributes.JUMP_STRENGTH, JUMP_MOD, WeightSystem.jumpFactor(jumpLoad));
            stillInside.add(mob.getUUID());
            // Drenaje: % de la vida máxima por segundo repartido en pasos de 5 ticks; nunca baja de 1.
            // La escritura directa de vida la traduce EntityStats.reconcile a body con suelo 1.
            if (damage && load > ServerConfig.gravityChamberDrainLoad() && mob.getHealth() > 1f) {
                float step = (float) (mob.getMaxHealth() * WeightSystem.drainPercentPerSecond(load) / 100.0
                        * ENTITY_INTERVAL / 20.0);
                mob.setHealth(Math.max(1f, mob.getHealth() - step));
            }
        }

        // Los que salieron desde la última vez: fuera modificadores.
        for (Iterator<UUID> it = affectedMobs.iterator(); it.hasNext(); ) {
            UUID id = it.next();
            if (stillInside.contains(id)) continue;
            if (level.getEntity(id) instanceof LivingEntity le) clearMods(le);
            it.remove();
        }
        affectedMobs.addAll(stillInside);
    }

    /** Modificador transitorio con nuestro id; solo se reescribe si cambia el valor. */
    private static void setMod(LivingEntity e, Holder<Attribute> attr, ResourceLocation id, double factor) {
        AttributeInstance inst = e.getAttribute(attr);
        if (inst == null) return;
        double amount = factor - 1.0;
        AttributeModifier cur = inst.getModifier(id);
        if (amount >= 0.0) {
            if (cur != null) inst.removeModifier(id);
            return;
        }
        if (cur != null && Math.abs(cur.amount() - amount) < 1.0e-4) return;
        inst.removeModifier(id);
        inst.addTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
    }

    private static void clearMods(LivingEntity e) {
        for (Holder<Attribute> a : java.util.List.of(Attributes.MOVEMENT_SPEED, Attributes.FLYING_SPEED)) {
            AttributeInstance i = e.getAttribute(a);
            if (i != null) i.removeModifier(MOVE_MOD);
        }
        AttributeInstance j = e.getAttribute(Attributes.JUMP_STRENGTH);
        if (j != null) j.removeModifier(JUMP_MOD);
    }

    /** Quita los modificadores a cada mob afectado (apagado, roto o descargado). */
    private void releaseMobs() {
        if (affectedMobs.isEmpty()) return;
        if (level instanceof ServerLevel sl) {
            for (UUID id : affectedMobs) {
                if (sl.getEntity(id) instanceof LivingEntity le) clearMods(le);
            }
        }
        affectedMobs.clear();
    }

    // ── Validación ───────────────────────────────────────────────────────────

    /**
     * Relleno desde las celdas libres pegadas a la consola. Transitable = colisión vacía (aire,
     * antorchas, hierba, fluidos); cualquier otro bloque es pared. Una celda alcanzada fuera de la
     * esfera es una fuga (se guardan hasta {@link #MAX_LEAKS}); un chunk sin cargar cuenta como fuga
     * (no se sabe qué hay y NO se carga). Público para los gametests.
     */
    public void validate() {
        if (level == null) return;
        boolean oldFormed = formed;
        int oldCount = interiorCount;
        long oldLeaks = hash(leaks), oldPorts = hash(ports);
        LongArrayList oldPortList = new LongArrayList(ports);

        interior.clear();
        leaks.clear();
        ports.clear();
        queue.clear();
        seenWalls.clear();
        LongOpenHashSet seenLeaks = new LongOpenHashSet();
        BlockPos center = sphereCenter(worldPosition, radius);
        int cx = center.getX(), cy = center.getY(), cz = center.getZ();
        long consoleKey = worldPosition.asLong();
        LevelChunk chunk = null;
        long chunkKey = Long.MIN_VALUE;

        queue.enqueue(consoleKey);
        boolean overflow = false;
        while (!queue.isEmpty() && leaks.size() < MAX_LEAKS) {
            long cur = queue.dequeueLong();
            int x = BlockPos.getX(cur), y = BlockPos.getY(cur), z = BlockPos.getZ(cur);
            for (int d = 0; d < 6; d++) {
                int nx = x + (d == 0 ? 1 : d == 1 ? -1 : 0);
                int ny = y + (d == 2 ? 1 : d == 3 ? -1 : 0);
                int nz = z + (d == 4 ? 1 : d == 5 ? -1 : 0);
                long key = BlockPos.asLong(nx, ny, nz);
                if (key == consoleKey || interior.contains(key) || seenWalls.contains(key) || seenLeaks.contains(key)) continue;
                int chX = nx >> 4, chZ = nz >> 4;
                long ck = ((long) chX << 32) ^ (chZ & 0xFFFFFFFFL);
                if (ck != chunkKey) {
                    chunkKey = ck;
                    chunk = level.hasChunk(chX, chZ) ? level.getChunk(chX, chZ) : null;
                }
                if (chunk == null || level.isOutsideBuildHeight(ny)) {
                    if (seenLeaks.add(key) && leaks.size() < MAX_LEAKS) leaks.add(key);
                    continue;
                }
                cursor.set(nx, ny, nz);
                BlockState st = chunk.getBlockState(cursor);
                boolean passable = st.isAir() || st.getCollisionShape(level, cursor).isEmpty();
                if (!passable) {
                    seenWalls.add(key);
                    if (st.is(ModBlocks.GRAVITY_CHAMBER_PORT.get())) ports.add(key);
                    continue;
                }
                if (!insideSphere(nx - cx, ny - cy, nz - cz, radius)) {
                    if (seenLeaks.add(key) && leaks.size() < MAX_LEAKS) leaks.add(key);
                    continue;
                }
                interior.add(key);
                if (interior.size() > MAX_INTERIOR) { overflow = true; break; }
                queue.enqueue(key);
            }
            if (overflow) break;
        }

        interiorCount = interior.size();
        formed = !overflow && leaks.isEmpty() && interiorCount >= MIN_INTERIOR;
        if (!formed) interior.clear();   // sin sala no hay campo
        if (!formed) ports.clear();

        // Enlazar los puertos nuevos y soltar los que ya no tocan la sala.
        for (long p : ports) {
            if (level.getBlockEntity(BlockPos.of(p)) instanceof GravityChamberPortBlockEntity port) port.link(worldPosition);
        }
        for (long p : oldPortList) {
            if (ports.contains(p)) continue;
            if (level.isLoaded(BlockPos.of(p)) && level.getBlockEntity(BlockPos.of(p)) instanceof GravityChamberPortBlockEntity port
                    && worldPosition.equals(port.controller())) {
                port.link(null);
            }
        }

        if (oldFormed != formed || oldCount != interiorCount || oldLeaks != hash(leaks) || oldPorts != hash(ports)) {
            setChanged();
            syncToClient();
        }
    }

    private static long hash(LongArrayList l) {
        long h = l.size();
        for (int i = 0; i < l.size(); i++) h = h * 31 + l.getLong(i);
        return h;
    }

    // ── Holograma (cliente) ──────────────────────────────────────────────────

    /**
     * Corteza mínima de la esfera de este radio: celdas FUERA de la esfera pegadas por una cara a una
     * de dentro (lo justo para cerrarla). Se calcula una vez por radio y se guarda.
     */
    public long[] shell() {
        if (shellRadius == radius) return shellCache;
        BlockPos c = sphereCenter(worldPosition, radius);
        LongArrayList out = new LongArrayList();
        int r = radius + 1;
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (insideSphere(dx, dy, dz, radius)) continue;
                    if (insideSphere(dx + 1, dy, dz, radius) || insideSphere(dx - 1, dy, dz, radius)
                            || insideSphere(dx, dy + 1, dz, radius) || insideSphere(dx, dy - 1, dz, radius)
                            || insideSphere(dx, dy, dz + 1, radius) || insideSphere(dx, dy, dz - 1, radius)) {
                        out.add(BlockPos.asLong(c.getX() + dx, c.getY() + dy, c.getZ() + dz));
                    }
                }
            }
        }
        shellCache = out.toLongArray();
        shellRadius = radius;
        missingAt = Long.MIN_VALUE;
        return shellCache;
    }

    /** Celdas de la corteza sin pared. Se recalcula como mucho cada 10 ticks, nunca por fotograma. */
    public long[] missingShell(long gameTime) {
        long[] s = shell();
        if (level == null) return new long[0];
        if (gameTime - missingAt < 10 && missingAt != Long.MIN_VALUE) return missingCache;
        missingAt = gameTime;
        LongArrayList out = new LongArrayList();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (long k : s) {
            m.set(k);
            BlockState st = level.getBlockState(m);
            if (st.isAir() || st.getCollisionShape(level, m).isEmpty()) out.add(k);
        }
        missingCache = out.toLongArray();
        return missingCache;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && level.isClientSide) CLIENT_LOADED.add(this);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        CLIENT_LOADED.remove(this);
        releaseMobs();
        droneRound.abort(level);
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        CLIENT_LOADED.remove(this);
        releaseMobs();
        droneRound.abort(level);
    }

    /** Al romperse: termina la ronda, suelta la mejora y los drones, libera a los mobs y desenlaza los puertos. */
    public void onBroken() {
        if (level == null) return;
        droneRound.abort(level);
        for (int i = 0; i < drones.getSlots(); i++) {
            ItemStack d = drones.getStackInSlot(i);
            if (d.isEmpty()) continue;
            Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5,
                    worldPosition.getZ() + 0.5, d.copy());
            drones.setStackInSlot(i, ItemStack.EMPTY);
        }
        ItemStack s = upgrade.getStackInSlot(0);
        if (!s.isEmpty()) {
            Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5,
                    worldPosition.getZ() + 0.5, s.copy());
            upgrade.setStackInSlot(0, ItemStack.EMPTY);
        }
        releaseMobs();
        for (long p : ports) {
            if (level.getBlockEntity(BlockPos.of(p)) instanceof GravityChamberPortBlockEntity port) port.link(null);
        }
        ports.clear();
    }

    // ── Menú ─────────────────────────────────────────────────────────────────

    @Override
    public @NotNull Component getDisplayName() { return Component.translatable("block.zenkai.gravity_chamber"); }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, @NotNull Inventory inv, @NotNull Player player) {
        return new GravityChamberMenu(id, inv, this);
    }

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            return switch (i) {
                case GravityChamberMenu.D_FE_LO -> energy.get() & 0xFFFF;
                case GravityChamberMenu.D_FE_HI -> energy.get() >>> 16;
                case GravityChamberMenu.D_CAP_LO -> energy.capacity() & 0xFFFF;
                case GravityChamberMenu.D_CAP_HI -> energy.capacity() >>> 16;
                case GravityChamberMenu.D_TARGET_G -> targetG;
                case GravityChamberMenu.D_MAX_G -> maxGravity();
                case GravityChamberMenu.D_STATUS -> status();
                case GravityChamberMenu.D_INTERIOR -> interiorCount;
                case GravityChamberMenu.D_LEAKS -> leaks.size();
                case GravityChamberMenu.D_RADIUS -> radius;
                case GravityChamberMenu.D_HOLOGRAM -> hologram ? 1 : 0;
                case GravityChamberMenu.D_COST -> active ? lastCost : costPerTick(effectiveGravity(), radius);
                case GravityChamberMenu.D_PORTS -> ports.size();
                case GravityChamberMenu.D_ENABLED -> enabled ? 1 : 0;
                case GravityChamberMenu.D_MAX_DRONES -> maxDrones();
                case GravityChamberMenu.D_DRONE_MODE -> aggressiveMode ? 1 : 0;
                case GravityChamberMenu.D_ROUND_RUNNING -> droneRound.running() ? 1 : 0;
                case GravityChamberMenu.D_ROUND_LEFT -> level == null ? 0 : droneRound.secondsLeft(level.getGameTime());
                case GravityChamberMenu.D_ROUND_DONE -> droneRound.doneCount();
                case GravityChamberMenu.D_ROUND_TOTAL -> droneRound.total();
                case GravityChamberMenu.D_LAST_RESULT -> droneRound.lastResult();
                case GravityChamberMenu.D_LAST_TP_LO -> droneRound.lastTp() & 0xFFFF;
                case GravityChamberMenu.D_LAST_TP_HI -> droneRound.lastTp() >>> 16;
                case GravityChamberMenu.D_ROUND_AGGRESSIVE -> droneRound.aggressive() ? 1 : 0;
                case GravityChamberMenu.D_DIFFICULTY -> droneDifficulty.ordinal();
                default -> 0;
            };
        }

        @Override public void set(int i, int v) { }

        @Override public int getCount() { return GravityChamberMenu.DATA_SLOTS; }
    };

    public ContainerData containerData() { return data; }
    public boolean droneView() { return droneView; }

    public void setDroneView(boolean v) {
        if (droneView == v) return;
        droneView = v;
        setChanged();
    }

    // ── NBT y sync ───────────────────────────────────────────────────────────

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider reg) {
        super.saveAdditional(tag, reg);
        energy.save(tag);
        tag.put("Upgrade", upgrade.serializeNBT(reg));
        tag.putInt("TargetG", targetG);
        tag.putInt("Radius", radius);
        tag.putBoolean("Hologram", hologram);
        tag.putBoolean("Enabled", enabled);
        tag.putLongArray("Ports", ports.toLongArray());
        tag.put("Drones", drones.serializeNBT(reg));
        tag.putBoolean("DroneAggressive", aggressiveMode);
        tag.putInt("DroneDifficulty", droneDifficulty.ordinal());
        tag.putBoolean("DroneView", droneView);
    }

    @Override
    protected void loadAdditional(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider reg) {
        super.loadAdditional(tag, reg);
        energy.load(tag);
        if (tag.contains("Upgrade")) ItemHandlers.loadKeepingSize(upgrade, reg, tag.getCompound("Upgrade"));
        targetG = Math.max(1, Math.min(500, tag.contains("TargetG") ? tag.getInt("TargetG") : 10));
        radius = Math.max(MIN_RADIUS, Math.min(MAX_RADIUS, tag.contains("Radius") ? tag.getInt("Radius") : DEFAULT_RADIUS));
        hologram = tag.getBoolean("Hologram");
        if (tag.contains("Enabled")) enabled = tag.getBoolean("Enabled");
        if (tag.contains("Drones")) ItemHandlers.loadKeepingSize(drones, reg, tag.getCompound("Drones"));
        aggressiveMode = tag.getBoolean("DroneAggressive");
        droneView = tag.getBoolean("DroneView");
        droneDifficulty = com.hmc.zenkai.feature.gravity.DroneDifficulty.byId(
                tag.contains("DroneDifficulty") ? tag.getInt("DroneDifficulty") : com.hmc.zenkai.feature.gravity.DroneDifficulty.NORMAL.ordinal());
        ports.clear();
        for (long p : tag.getLongArray("Ports")) ports.add(p);
        // Lo que solo viaja al cliente (no se guarda en disco).
        if (tag.contains("SyncFormed")) {
            formed = tag.getBoolean("SyncFormed");
            active = tag.getBoolean("SyncActive");
            interiorCount = tag.getInt("SyncInterior");
            leaks.clear();
            for (long l : tag.getLongArray("SyncLeaks")) leaks.add(l);
            shellRadius = -1;   // el radio pudo cambiar: la corteza se recalcula
        }
        needsValidate = true;
    }

    @Override
    public @NotNull CompoundTag getUpdateTag(@NotNull HolderLookup.Provider reg) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Radius", radius);
        tag.putBoolean("Hologram", hologram);
        tag.putInt("TargetG", targetG);
        tag.putBoolean("SyncFormed", formed);
        tag.putBoolean("SyncActive", active);
        tag.putInt("SyncInterior", interiorCount);
        tag.putLongArray("SyncLeaks", leaks.toLongArray());
        return tag;
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    private void syncToClient() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    /** Para gametests: el repaso de entidades sin esperar al múltiplo de 5 ticks. */
    public void tickEntitiesForTests() {
        if (level instanceof ServerLevel sl && active) tickEntities(sl, sl.getGameTime());
    }

    /** Para gametests. */
    public void setTargetGravityForTests(int g) { targetG = g; }
    public void setRadiusForTests(int r) { radius = r; }
    public boolean interiorContains(BlockPos p) { return interior.contains(p.asLong()); }

    // ── Búfer de FE: solo entrada, gasto interno (patrón de BenchEnergy) ─────

    public static final class Energy implements IEnergyStorage {
        private int energy;

        public int get() { return energy; }
        public int capacity() { return ServerConfig.gravityChamberFeCapacity(); }

        boolean spend(int amount) {
            if (amount <= 0) return true;
            if (energy < amount) return false;
            energy -= amount;
            return true;
        }

        void load(CompoundTag tag) { energy = Math.max(0, Math.min(capacity(), tag.getInt("Energy"))); }

        void save(CompoundTag tag) { tag.putInt("Energy", energy); }

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            int accepted = Math.min(capacity() - energy, Math.min(ServerConfig.gravityChamberMaxReceive(), maxReceive));
            if (accepted <= 0) return 0;
            if (!simulate) energy += accepted;
            return accepted;
        }

        @Override public int extractEnergy(int maxExtract, boolean simulate) { return 0; }
        @Override public int getEnergyStored() { return energy; }
        @Override public int getMaxEnergyStored() { return capacity(); }
        @Override public boolean canExtract() { return false; }
        @Override public boolean canReceive() { return true; }
    }

    /** Solo para gametests: llena el búfer sin pasar por el tope de entrada. */
    public void fillEnergyForTests(int fe) { energy.energy = Math.min(energy.capacity(), fe); }
    public int energyStored() { return energy.get(); }
}
