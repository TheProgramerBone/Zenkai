package com.hmc.db_renewed.network.ki;

import com.hmc.db_renewed.entity.ModEntities;
import com.hmc.db_renewed.entity.ki_attacks.ki_blast.KiBlastEntity;
import com.hmc.db_renewed.network.stats.PlayerLifeCycle;
import com.hmc.db_renewed.network.stats.PlayerStatsAttachment;
import com.hmc.db_renewed.network.stats.PlayerVisualAttachment;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class KiAttackServerLogic {

    private KiAttackServerLogic() {}

    /** Entidad que estamos cargando + tick de inicio. */
    private record ActiveCharge(UUID entityId, long startTick) {}

    private static final Map<UUID, ActiveCharge> ACTIVE = new HashMap<>();

    // --- Parámetros de balance SOLO de tiempo de carga ---
    public static final long MAX_BASE_CHARGE_TICKS   = 60L;  // 0→100%
    public static final long MAX_OVER_CHARGE_TICKS   = 60L;  // 100→200%
    public static final long MAX_TOTAL_CHARGE_TICKS  = MAX_BASE_CHARGE_TICKS + MAX_OVER_CHARGE_TICKS;

    // =====================================================
    //                    START CHARGE
    // =====================================================
    /** Llamado cuando llega ChargeKiAttackPacket(true). */
    public static void startCharging(ServerPlayer sp) {
        Level level = sp.level();
        if (level.isClientSide) return;

        if (ACTIVE.containsKey(sp.getUUID())) {
            sp.sendSystemMessage(Component.literal("[SERVER] startCharging: ya hay un blast activo, ignorando."));
            return;
        }

        long now = level.getGameTime();

        KiBlastEntity blast = new KiBlastEntity(ModEntities.KI_BLAST.get(), level);

        // Posición inicial: 1 bloque frente al jugador, ligeramente por debajo de los ojos
        Vec3 look = sp.getLookAngle();
        double dist = 1.0; // ≈ 1 bloque
        double x = sp.getX() + look.x * dist;
        double y = sp.getEyeY() - 0.2;
        double z = sp.getZ() + look.z * dist;

        blast.moveTo(x, y, z, sp.getYRot(), sp.getXRot());
        blast.setOwner(sp);
        blast.setNoGravity(true);
        blast.setDeltaMovement(Vec3.ZERO);
        blast.setCharging(true); // animación de carga en la entidad

        level.addFreshEntity(blast);

        ACTIVE.put(sp.getUUID(), new ActiveCharge(blast.getUUID(), now));
    }

    // =====================================================
    //                   RELEASE CHARGE
    // =====================================================
    /** Llamado cuando llega ChargeKiAttackPacket(false). */
    public static void releaseCharging(ServerPlayer sp) {
        Level level = sp.level();
        if (level.isClientSide) return;

        ActiveCharge active = ACTIVE.remove(sp.getUUID());
        if (active == null) {
            return;
        }
        if (!(level instanceof ServerLevel sl)) {
            return;
        }

        Entity e = sl.getEntity(active.entityId());
        if (!(e instanceof KiBlastEntity blast)) {
            return;
        }

        long now = level.getGameTime();
        long ticksCharged  = Math.max(0L, now - active.startTick());
        long clampedTicks  = Math.min(ticksCharged, MAX_TOTAL_CHARGE_TICKS);

        double chargeRatio;
        if (clampedTicks <= MAX_BASE_CHARGE_TICKS) {
            chargeRatio = clampedTicks / (double) MAX_BASE_CHARGE_TICKS;
        } else {
            long over = clampedTicks - MAX_BASE_CHARGE_TICKS;
            chargeRatio = 1.0 + over / (double) MAX_OVER_CHARGE_TICKS;
        }

        PlayerStatsAttachment stats = PlayerStatsAttachment.get(sp);

        String selectedId = stats.getSelectedKiAttackId();
        KiAttackDefinition def = stats.getKiAttack(selectedId);

// Si no hay ataque seleccionado o no existe, intentamos usar basic_blast
        if (def == null) {
            def = stats.getKiAttack("basic_blast");
        }

// Si tampoco existe basic_blast, lo creamos AQUÍ y lo guardamos en los stats
        if (def == null) {
            PlayerVisualAttachment vis = PlayerVisualAttachment.get(sp);
            // 1) Color base de aura desde visual attachment si existe
            int auraColor = vis.getAuraColorRgb();
            try {

                 // Fallback por compatibilidad
                auraColor = vis.getAuraColorRgb();
            } catch (Exception ignored) {
                // En caso de que todavía no esté registrado el attachment
            }

            def = new KiAttackDefinition(
                    "basic_blast",
                    "Basic Blast",
                    KiAttackType.BLAST,
                    4.0,     // basePower
                    1.0,     // speed
                    20,      // cooldownTicks
                    20,      // chargeTimeTicks
                    1,       // density
                    auraColor  // ahora sale del sistema visual
            );

            // Lo guardamos para futuros disparos
            stats.addOrUpdateKiAttack(def);
            stats.setSelectedKiAttackId("basic_blast");
        }

        int kiCost = stats.computeKiAttackCost(def, chargeRatio);
        int currentKi = stats.getKiCurrent();

        if (currentKi < kiCost) {
            blast.discard();
            return;
        }

        // Consumir KI y sync
        stats.addKi(-kiCost);
        PlayerLifeCycle.syncIfServer(sp);

        float damage = stats.computeKiAttackDamage(def, chargeRatio);
        blast.setCharging(false);
        blast.setPower(damage);

        double defSpeed = def.speed() <= 0 ? 0.8 : def.speed();
        double speedMult = 0.6 + 0.4 * chargeRatio;
        double speed = defSpeed * speedMult;

        Vec3 look = sp.getLookAngle();
        Vec3 vel  = look.scale(speed);

        blast.setNoGravity(true);
        blast.setDeltaMovement(vel);
    }
}