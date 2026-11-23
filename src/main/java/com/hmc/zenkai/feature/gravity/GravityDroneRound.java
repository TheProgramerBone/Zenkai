package com.hmc.zenkai.feature.gravity;

import com.hmc.zenkai.config.ServerConfig;
import com.hmc.zenkai.content.blockentity.GravityChamberBlockEntity;
import com.hmc.zenkai.content.entity.misc.TrainingDroneEntity;
import com.hmc.zenkai.content.item.TrainingDroneItem;
import com.hmc.zenkai.feature.training.TrainingHooks;
import com.hmc.zenkai.registry.ModEntities;
import com.hmc.zenkai.util.ZenkaiNumbers;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Ronda de drones de la cámara de gravedad (Fase 4 de la 0.7.0; diseño en pendientes-0.7.0.md §3.3, P5 y P9).
 * Vive dentro de {@link GravityChamberBlockEntity}: una consola, una ronda a la vez, un dueño (quien pulsa
 * «Iniciar ronda» desde dentro de la sala).
 *
 * <ul>
 *   <li><b>Cuántos drones:</b> {@code min(maxDrones(), drones con usos en las ranuras)}; maxDrones = radio − 2.</li>
 *   <li><b>Pasivo:</b> blancos que no atacan; golpear cada uno una vez antes de que acabe el tiempo
 *       (base + por dron, según la {@link DroneDifficulty}). El dron golpeado cambia de color.</li>
 *   <li><b>Agresivo:</b> disparan ki; destruir cada uno ({@code hitsToBreak} golpes del dueño) antes del tope.</li>
 *   <li><b>Dificultad:</b> se fija al empezar (cambiarla a mitad no afecta a la ronda en curso); tabla y
 *       multiplicador de TP en {@link DroneDifficulty}.</li>
 *   <li><b>Fuera de combate (agresivo, pedido del usuario 2026-11-22):</b> los drones nunca matan; si un disparo te
 *       deja en 1 de body la ronda TERMINA y paga el TP de los drones ya destruidos (la única excepción al todo o nada).</li>
 *   <li><b>Todo o nada (P9):</b> por lo demás, solo una ronda COMPLETA da TP. Salir de la sala, desconectarse, morir, perder la
 *       energía (la g vuelve a 1, P5) o perder un dron la CANCELA sin TP; agotar el tiempo, igual.</li>
 *   <li><b>Usos:</b> gasta uno el dron golpeado o destruido, en el momento del golpe: así cuenta también en una
 *       ronda cancelada («gastan su uso como siempre», P5). Las ranuras de drones quedan BLOQUEADAS durante la
 *       ronda: sacar el ítem antes del golpe esquivaría el gasto.</li>
 *   <li><b>TP:</b> {@code droneTpPerG × g × n × tpMult} (× {@code droneAggressiveTpMult} en agresivo), con la g MÍNIMA que
 *       hubo durante la ronda (empezar a ×500 y bajar a ×2 para rematar no paga ×500). Va por la categoría de
 *       fatiga GRAVITY y por el embudo normal de TP (fatiga, pesas × gravedad, HTC, raza).</li>
 * </ul>
 * Nada de esto se guarda en disco: las entidades no se guardan (tipo con {@code noSave()}) y
 * un reinicio o descarga del chunk termina la ronda sin TP, que es el error barato.
 */
public final class GravityDroneRound {

    public static final int RESULT_NONE = 0, RESULT_SUCCESS = 1, RESULT_TIMEOUT = 2, RESULT_CANCELLED = 3, RESULT_DEFEATED = 4;
    public static final int MAX_DRONES = 8;
    /** Ticks entre repasos de la ronda (los mismos que el repaso de entidades de la consola). */
    public static final int TICK_INTERVAL = 5;

    private final GravityChamberBlockEntity be;

    private boolean running;
    @Nullable private UUID owner;
    private boolean aggressive;
    private DroneDifficulty difficulty = DroneDifficulty.NORMAL;
    private long endTick;
    private long durationTicks;
    private int minG;
    private final List<UUID> drones = new ArrayList<>();
    private final List<Integer> slots = new ArrayList<>();
    private final List<Boolean> done = new ArrayList<>();
    /** Celdas del interior por las que pueden volar (con una celda libre encima), muestreadas al empezar. */
    private long[] flightCells = new long[0];

    private int lastResult = RESULT_NONE;
    private int lastTp;

    public GravityDroneRound(GravityChamberBlockEntity be) { this.be = be; }

    // ── Lectura ──────────────────────────────────────────────────────────────

    public boolean running() { return running; }
    public boolean aggressive() { return aggressive; }
    public DroneDifficulty difficulty() { return difficulty; }
    @Nullable public UUID owner() { return owner; }
    public int total() { return running ? drones.size() : 0; }
    public int doneCount() {
        int n = 0;
        for (boolean d : done) if (d) n++;
        return n;
    }
    public int lastResult() { return lastResult; }
    public int lastTp() { return lastTp; }
    public long[] flightCells() { return flightCells; }

    /** Segundos que quedan (0 sin ronda). */
    public int secondsLeft(long now) {
        if (!running) return 0;
        return (int) Math.max(0, (endTick - now + 19) / 20);
    }

    /** TP crudo que pagaría completar una ronda con estos datos (antes de fatiga y multiplicadores). */
    public static double rawTp(int g, int drones, boolean aggressive, DroneDifficulty difficulty) {
        if (g <= 1 || drones <= 0) return 0;
        return ServerConfig.droneTpPerG() * g * drones * difficulty.tpMult
                * (aggressive ? ServerConfig.droneAggressiveTpMult() : 1.0);
    }

    /** ¿El dron de esta entidad sigue contando en la ronda? Si no, la entidad se borra sola. */
    public boolean owns(TrainingDroneEntity e) {
        int i = drones.indexOf(e.getUUID());
        return running && i >= 0 && !(aggressive && done.get(i));
    }

    /** Usos restantes contados en las ranuras (para la pantalla y el arranque). */
    public static int usableDrones(ItemStackHandler h) {
        int n = 0;
        for (int i = 0; i < h.getSlots(); i++) {
            if (h.getStackInSlot(i).getItem() instanceof TrainingDroneItem && TrainingDroneItem.usesLeft(h.getStackInSlot(i)) > 0) n++;
        }
        return n;
    }

    // ── Arranque ─────────────────────────────────────────────────────────────

    /**
     * Empieza una ronda. Devuelve la clave de lang del motivo si no puede (y no cambia nada), o null.
     * Requisitos: gravedad activa de verdad (> ×1 y con energía), el jugador DENTRO de la sala, sin otra
     * ronda en marcha y al menos un dron con usos.
     */
    @Nullable
    public String start(ServerLevel level, ServerPlayer sp, boolean aggressiveMode, DroneDifficulty diff) {
        if (running) return "messages.zenkai.drones.already_running";
        if (be.status() != GravityChamberBlockEntity.STATUS_ACTIVE) return "messages.zenkai.drones.need_gravity";
        if (!be.interiorContains(sp.blockPosition())) return "messages.zenkai.drones.need_inside";
        ItemStackHandler h = be.droneHandler();
        int want = Math.min(be.maxDrones(), usableDrones(h));
        if (want <= 0) return "messages.zenkai.drones.no_drones";

        long[] cells = sampleFlightCells(level);
        if (cells.length == 0) return "messages.zenkai.drones.need_gravity";

        clearLists();
        flightCells = cells;
        aggressive = aggressiveMode;
        difficulty = diff;
        owner = sp.getUUID();
        minG = be.effectiveGravity();
        long now = level.getGameTime();
        durationTicks = difficulty.roundTicks(aggressive, want);
        endTick = now + durationTicks;

        for (int slot = 0; slot < h.getSlots() && drones.size() < want; slot++) {
            ItemStack s = h.getStackInSlot(slot);
            if (!(s.getItem() instanceof TrainingDroneItem) || TrainingDroneItem.usesLeft(s) <= 0) continue;
            TrainingDroneEntity e = new TrainingDroneEntity(ModEntities.TRAINING_DRONE.get(), level);
            BlockPos cell = BlockPos.of(cells[level.random.nextInt(cells.length)]);
            e.moveTo(cell.getX() + 0.5, cell.getY() + 0.25, cell.getZ() + 0.5, level.random.nextFloat() * 360f, 0f);
            e.setup(be.getBlockPos(), sp.getUUID(), aggressive, difficulty, TrainingDroneItem.wearStage(s));
            if (!level.addFreshEntity(e)) continue;
            drones.add(e.getUUID());
            slots.add(slot);
            done.add(false);
        }
        if (drones.isEmpty()) { clearLists(); return "messages.zenkai.drones.no_drones"; }
        running = true;
        lastResult = RESULT_NONE;
        lastTp = 0;
        level.playSound(null, be.getBlockPos(), SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.8f, 1.6f);
        sp.displayClientMessage(Component.translatable(aggressive ? "messages.zenkai.drones.start_aggressive"
                : "messages.zenkai.drones.start_passive", drones.size(), ZenkaiNumbers.screen(durationTicks / 20)), true);
        be.setChanged();
        return null;
    }

    /** Interior con una celda libre encima (el dron mide ~0,6 y flota): por ahí pueden volar. */
    private long[] sampleFlightCells(ServerLevel level) {
        it.unimi.dsi.fastutil.longs.LongArrayList out = new it.unimi.dsi.fastutil.longs.LongArrayList();
        BlockPos console = be.getBlockPos();
        for (long k : be.interiorCells()) {
            BlockPos p = BlockPos.of(k);
            if (p.getY() <= console.getY()) continue;          // la capa del suelo, no: ahí está el jugador
            if (be.interiorContainsKey(BlockPos.asLong(p.getX(), p.getY() + 1, p.getZ()))) out.add(k);
        }
        if (out.isEmpty()) {                                   // sala muy baja: cualquier celda del interior
            for (long k : be.interiorCells()) if (k != console.asLong()) out.add(k);
        }
        return out.toLongArray();
    }

    // ── Repaso ───────────────────────────────────────────────────────────────

    /** Cada {@link #TICK_INTERVAL} ticks desde el tick de la consola. */
    public void tick(ServerLevel level, long now) {
        if (!running) return;
        ServerPlayer sp = owner == null ? null : (level.getPlayerByUUID(owner) instanceof ServerPlayer p ? p : null);
        if (sp == null || sp.level() != level || !sp.isAlive()) { finish(level, RESULT_CANCELLED, "messages.zenkai.drones.cancel_left"); return; }
        if (!be.interiorContains(sp.blockPosition())) { finish(level, RESULT_CANCELLED, "messages.zenkai.drones.cancel_left"); return; }
        if (!be.isActive()) { finish(level, RESULT_CANCELLED, "messages.zenkai.drones.cancel_energy"); return; }
        minG = Math.min(minG, be.effectiveGravity());
        for (int i = 0; i < drones.size(); i++) {
            if (done.get(i) && aggressive) continue;
            Entity e = level.getEntity(drones.get(i));
            if (!(e instanceof TrainingDroneEntity) || !e.isAlive()) { finish(level, RESULT_CANCELLED, "messages.zenkai.drones.cancel_lost"); return; }
        }
        if (now >= endTick) { finish(level, RESULT_TIMEOUT, "messages.zenkai.drones.timeout"); return; }
        // Aviso de los últimos 5 segundos en la barra de acción (el temporizador entero se ve en la consola).
        int left = secondsLeft(now);
        if (left <= 5 && (endTick - now) % 20 < TICK_INTERVAL) {
            sp.displayClientMessage(Component.translatable("messages.zenkai.drones.seconds_left", left).withStyle(ChatFormatting.YELLOW), true);
        }
    }

    /**
     * Golpe del dueño a un dron (lo llama {@link TrainingDroneEntity#hurt}). Pasivo: lo marca y gasta el uso.
     * Agresivo: cuenta el golpe y al llegar a {@code hitsToBreak} de la dificultad lo destruye y gasta el uso.
     * @return true si el golpe contó
     */
    public boolean onDroneHit(ServerLevel level, TrainingDroneEntity e, ServerPlayer attacker) {
        if (!running || !attacker.getUUID().equals(owner)) return false;
        int i = drones.indexOf(e.getUUID());
        if (i < 0 || done.get(i)) return false;
        if (aggressive) {
            int hits = e.addHit();
            if (hits < difficulty.hitsToBreak) {
                level.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.IRON_GOLEM_HURT, SoundSource.PLAYERS, 0.6f, 1.8f);
                return true;
            }
            e.burst();
            e.discard();
        } else {
            e.markHit();
            level.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 0.9f, 1.4f + 0.1f * doneCount());
        }
        done.set(i, true);
        spendUse(level, slots.get(i));
        if (doneCount() >= drones.size()) finish(level, RESULT_SUCCESS, null);
        else be.setChanged();
        return true;
    }

    /** Gasta un uso del dron de esa ranura; al agotarse se rompe (desaparece de la ranura). */
    private void spendUse(ServerLevel level, int slot) {
        ItemStackHandler h = be.droneHandler();
        ItemStack s = h.getStackInSlot(slot);
        if (!(s.getItem() instanceof TrainingDroneItem)) return;
        ItemStack copy = s.copy();
        copy.setDamageValue(copy.getDamageValue() + 1);
        if (copy.getDamageValue() >= copy.getMaxDamage()) {
            be.setDroneDirect(slot, ItemStack.EMPTY);
            level.playSound(null, be.getBlockPos(), SoundEvents.ITEM_BREAK, SoundSource.BLOCKS, 0.8f, 1.0f);
        } else {
            be.setDroneDirect(slot, copy);
        }
    }

    // ── Final ────────────────────────────────────────────────────────────────

    private void finish(ServerLevel level, int result, @Nullable String messageKey) {
        ServerPlayer sp = owner == null ? null : (level.getPlayerByUUID(owner) instanceof ServerPlayer p ? p : null);
        int granted = 0;
        if (result == RESULT_DEFEATED && sp != null) {
            int destroyed = doneCount();
            granted = destroyed > 0 ? TrainingHooks.grantFromGravityDrones(sp, rawTp(minG, destroyed, aggressive, difficulty)) : 0;
            sp.displayClientMessage(Component.translatable("messages.zenkai.drones.defeated",
                    ZenkaiNumbers.screen((long) destroyed), ZenkaiNumbers.screen((long) drones.size()),
                    ZenkaiNumbers.screen((long) granted)).withStyle(ChatFormatting.GOLD), true);
            level.playSound(null, be.getBlockPos(), SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 0.8f, 1.0f);
        } else if (result == RESULT_SUCCESS && sp != null) {
            granted = TrainingHooks.grantFromGravityDrones(sp, rawTp(minG, drones.size(), aggressive, difficulty));
            sp.displayClientMessage(Component.translatable("messages.zenkai.drones.success",
                    ZenkaiNumbers.screen((long) granted)).withStyle(ChatFormatting.GREEN), true);
            level.playSound(null, be.getBlockPos(), SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 0.7f, 1.2f);
        } else if (sp != null && messageKey != null) {
            sp.displayClientMessage(Component.translatable(messageKey).withStyle(ChatFormatting.RED), true);
            level.playSound(null, be.getBlockPos(), SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 0.8f, 1.4f);
        }
        removeEntities(level);
        lastResult = result;
        lastTp = granted;
        running = false;
        clearLists();
        be.setChanged();
    }

    /**
     * El dueño quedó en 1 de body por un disparo (solo agresivo): termina con el TP de los drones ya destruidos.
     */
    public void defeat(ServerLevel level) {
        if (running && aggressive) finish(level, RESULT_DEFEATED, null);
    }

    /** Cancela sin TP (botón de la consola). */
    public void cancel(ServerLevel level) {
        if (running) finish(level, RESULT_CANCELLED, "messages.zenkai.drones.cancel_button");
    }

    /** Termina sin mensajes ni TP (consola rota, descargada o quitada): los drones vuelven a la máquina. */
    public void abort(@Nullable net.minecraft.world.level.Level level) {
        if (!running) return;
        if (level instanceof ServerLevel sl) removeEntities(sl);
        running = false;
        lastResult = RESULT_CANCELLED;
        lastTp = 0;
        clearLists();
    }

    private void removeEntities(ServerLevel level) {
        for (UUID id : drones) {
            Entity e = level.getEntity(id);
            if (e != null) e.discard();
        }
    }

    private void clearLists() {
        drones.clear();
        slots.clear();
        done.clear();
        owner = running ? owner : null;
    }
}
