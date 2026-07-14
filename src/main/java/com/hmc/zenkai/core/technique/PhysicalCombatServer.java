package com.hmc.zenkai.core.technique;

import com.hmc.zenkai.core.ModGameRules;
import com.hmc.zenkai.core.network.feature.combat.CombatModeServerState;
import com.hmc.zenkai.core.network.feature.player.PlayerLifeCycle;
import com.hmc.zenkai.core.network.feature.player.PlayerStatsAttachment;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Ejecución server de técnicas físicas: validación (modo combate, manos vacías, no
 * bloqueando/derribado, desbloqueada, cooldown, stamina) + efecto. El daño sale por el
 * pipeline normal (playerAttack) con dos puentes hacia CombatZenkaiHooks:
 *  - FIRING evita que la rama atacante recalcule el golpe como melee básico.
 *  - defenseScale hace que la defensa del rival escale con el dmgMult de la técnica
 *    (misma regla unificada que el ki).
 */
public final class PhysicalCombatServer {
    private PhysicalCombatServer() {}

    /** Cooldowns por jugador+técnica (gameTime de disponibilidad). */
    private static final Map<UUID, long[]> COOLDOWNS = new HashMap<>();

    // Puente con CombatZenkaiHooks (server thread único).
    private static boolean firing = false;
    private static double defenseScale = 1.0;
    public static boolean isFiring()          { return firing; }
    public static double currentDefenseScale() { return defenseScale; }

    public static void tryExecute(ServerPlayer sp, PhysicalTechnique t) {
        PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
        if (!att.isCombatActive() || !CombatModeServerState.isActive(sp)) return;
        if (KiCombatServer.isBlocking(sp) || att.flags().isDowned()) return;
        if (!sp.getMainHandItem().isEmpty() || !sp.getOffhandItem().isEmpty()) return;
        if (!att.techniques().isUnlocked(t)) return;

        long now = sp.level().getGameTime();
        long[] cds = COOLDOWNS.computeIfAbsent(sp.getUUID(),
                k -> new long[PhysicalTechnique.values().length]);
        if (now < cds[t.ordinal()]) return;

        int cost = (int) Math.ceil(att.getStaminaMax() * t.staminaPct);
        if (att.getStamina() < cost) return;

        att.consumeStamina(cost);
        cds[t.ordinal()] = now + t.cooldownTicks;

        double str = att.computeMeleeFinal();
        firing = true;
        defenseScale = Math.max(0.1, t.dmgMult);
        try {
            switch (t) {
                case DASH_PUNCH -> dashPunch(sp, str, t);
                case HEAVY_BLOW -> heavyBlow(sp, str, t);
                case BARRAGE    -> barrage(sp, str, t);
                case KIAI       -> kiai(sp, t);
            }
        } finally {
            firing = false;
            defenseScale = 1.0;
        }
        PlayerLifeCycle.sync(sp);
    }

    /** Embestida corta hacia el frente + golpe al primer objetivo en la línea. */
    private static void dashPunch(ServerPlayer sp, double str, PhysicalTechnique t) {
        Vec3 look = sp.getLookAngle();
        sp.setDeltaMovement(look.x * 1.6, Math.min(look.y * 1.6, 0.3) + 0.1, look.z * 1.6);
        sp.hurtMarked = true; // fuerza el sync de velocidad al cliente
        LivingEntity target = firstInRay(sp, t.range, 1.2);
        if (target != null && damageEnabled(sp)) {
            target.hurt(sp.damageSources().playerAttack(sp), (float) (str * t.dmgMult));
        }
    }

    /** Golpe único pesado con gran knockback. */
    private static void heavyBlow(ServerPlayer sp, double str, PhysicalTechnique t) {
        LivingEntity target = firstInRay(sp, t.range, 1.0);
        if (target == null) return;
        if (damageEnabled(sp)) {
            target.hurt(sp.damageSources().playerAttack(sp), (float) (str * t.dmgMult));
        }
        Vec3 look = sp.getLookAngle();
        target.knockback(1.8, -look.x, -look.z);
    }

    /** Ráfaga en cono corto: daña a todos los objetivos del cono. */
    private static void barrage(ServerPlayer sp, double str, PhysicalTechnique t) {
        if (!damageEnabled(sp)) return;
        for (LivingEntity e : inCone(sp, t.range, 0.5)) {
            e.hurt(sp.damageSources().playerAttack(sp), (float) (str * t.dmgMult));
        }
    }

    /** Kiai: empujón en cono sin daño; desvía proyectiles ki ajenos. */
    private static void kiai(ServerPlayer sp, PhysicalTechnique t) {
        Vec3 look = sp.getLookAngle();
        for (LivingEntity e : inCone(sp, t.range, 0.4)) {
            e.setDeltaMovement(e.getDeltaMovement().add(look.x * 1.2, 0.35, look.z * 1.2));
            e.hurtMarked = true;
        }
        AABB box = sp.getBoundingBox().inflate(t.range);
        for (var proj : sp.serverLevel().getEntitiesOfClass(
                com.hmc.zenkai.content.entity.technique.KiProjectileEntity.class, box,
                pr -> pr.getOwner() != sp)) {
            double speed = proj.getDeltaMovement().length();
            proj.setDeltaMovement(look.scale(Math.max(speed, 0.6)));
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static boolean damageEnabled(ServerPlayer sp) {
        return sp.getServer() == null || ModGameRules.enableRaceBoosts(sp.getServer());
    }

    /** Primer LivingEntity dentro del cilindro (radio width) a lo largo de la mirada. */
    private static LivingEntity firstInRay(ServerPlayer sp, double range, double width) {
        Vec3 eye = sp.getEyePosition();
        Vec3 look = sp.getLookAngle();
        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        AABB box = sp.getBoundingBox().expandTowards(look.scale(range)).inflate(width);
        for (LivingEntity e : sp.serverLevel().getEntitiesOfClass(LivingEntity.class, box,
                x -> x != sp && x.isAlive())) {
            Vec3 to = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(eye);
            double along = to.dot(look);
            if (along < 0 || along > range) continue;
            double off = to.subtract(look.scale(along)).length();
            if (off > width + e.getBbWidth() * 0.5) continue;
            if (along < bestDist) { bestDist = along; best = e; }
        }
        return best;
    }

    /** Todos los LivingEntity en cono frontal (dot(mirada, dirección) > minDot). */
    private static java.util.List<LivingEntity> inCone(ServerPlayer sp, double range, double minDot) {
        Vec3 eye = sp.getEyePosition();
        Vec3 look = sp.getLookAngle();
        return sp.serverLevel().getEntitiesOfClass(LivingEntity.class,
                sp.getBoundingBox().inflate(range),
                e -> {
                    if (e == sp || !e.isAlive()) return false;
                    Vec3 to = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(eye);
                    if (to.length() > range) return false;
                    return to.normalize().dot(look) > minDot;
                });
    }

    public static void clear(UUID id) { COOLDOWNS.remove(id); }
}