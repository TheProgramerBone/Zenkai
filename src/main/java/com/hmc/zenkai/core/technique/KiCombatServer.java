package com.hmc.zenkai.core.technique;

import com.hmc.zenkai.content.entity.ModEntities;
import com.hmc.zenkai.content.entity.technique.KiProjectileEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Estado SERVIDOR del combate ki (v1.0):
 *
 *  - Cooldown global de disparo (FIRE_COOLDOWN_TICKS entre disparos por jugador).
 *  - Barrera: pool de absorción = kiPower × BARRIER_ABSORB_MULT × sizeF, dura
 *    BARRIER_DURATION_TICKS. absorb() se llama desde CombatZenkaiHooks ANTES de aplicar
 *    daño al body del jugador; consume pool y devuelve el daño restante. Al agotarse o
 *    expirar, el visual (KiProjectileEntity tipo BARRIER) se descarta.
 *
 * Constantes documentadas aquí (promover a StatsConfig cuando haga falta tunear en vivo).
 */
public final class KiCombatServer {
    private KiCombatServer() {}

    public static final int FIRE_COOLDOWN_TICKS = 10;      // 0.5 s entre disparos
    public static final double BARRIER_ABSORB_MULT = 3.0;  // pool = kiPower * mult * sizeF
    public static final int BARRIER_DURATION_TICKS = 200;  // 10 s

    /** Escalado por tamaño (1..7) compartido con daño/coste: 1.0 .. 2.5. */
    public static double sizeFactor(int size) {
        return 1.0 + 0.25 * (size - 1);
    }

    /** Escalado del COSTE por tamaño: 1.0 .. 2.2 (algo más barato que el daño: premia invertir). */
    public static double costSizeFactor(int size) {
        return 1.0 + 0.2 * (size - 1);
    }

    private record Barrier(double pool, long expiryTick, int entityId) {}

    private static final Map<UUID, Long> LAST_FIRE = new ConcurrentHashMap<>();
    private static final Map<UUID, Barrier> BARRIERS = new ConcurrentHashMap<>();

    /** true si puede disparar ya (y registra el disparo). */
    public static boolean tryFire(ServerPlayer sp) {
        long now = sp.level().getGameTime();
        Long last = LAST_FIRE.get(sp.getUUID());
        if (last != null && now - last < FIRE_COOLDOWN_TICKS) return false;
        LAST_FIRE.put(sp.getUUID(), now);
        return true;
    }

    /** Activa (o reemplaza) la barrera del jugador y crea su visual. */
    public static void activateBarrier(ServerPlayer sp, KiTechnique tech, double kiPower) {
        removeBarrier(sp); // una sola barrera activa

        double pool = kiPower * BARRIER_ABSORB_MULT * sizeFactor(tech.size());
        long expiry = sp.level().getGameTime() + BARRIER_DURATION_TICKS;

        KiProjectileEntity visual = new KiProjectileEntity(ModEntities.KI_PROJECTILE.get(), sp.level());
        visual.configure(sp, KiTechniqueType.BARRIER, tech.rgb(), tech.size(),
                0, BARRIER_DURATION_TICKS);
        visual.setPos(sp.getX(), sp.getY(), sp.getZ());
        sp.level().addFreshEntity(visual);

        BARRIERS.put(sp.getUUID(), new Barrier(pool, expiry, visual.getId()));
    }

    /**
     * Reduce el daño entrante con la barrera activa (si hay). Devuelve el daño restante.
     * Llamar desde CombatZenkaiHooks antes de aplicar el daño al body del jugador.
     */
    public static double absorb(ServerPlayer sp, double damage) {
        Barrier b = BARRIERS.get(sp.getUUID());
        if (b == null) return damage;

        long now = sp.level().getGameTime();
        if (now >= b.expiryTick()) {
            removeBarrier(sp);
            return damage;
        }

        double absorbed = Math.min(b.pool(), damage);
        double remainingPool = b.pool() - absorbed;
        if (remainingPool <= 0) {
            removeBarrier(sp); // pool agotado: rompe la barrera (y su visual)
        } else {
            BARRIERS.put(sp.getUUID(), new Barrier(remainingPool, b.expiryTick(), b.entityId()));
        }
        return damage - absorbed;
    }

    private static void removeBarrier(ServerPlayer sp) {
        Barrier b = BARRIERS.remove(sp.getUUID());
        if (b != null && sp.level() instanceof ServerLevel sl) {
            Entity e = sl.getEntity(b.entityId());
            if (e instanceof KiProjectileEntity) e.discard();
        }
    }
}