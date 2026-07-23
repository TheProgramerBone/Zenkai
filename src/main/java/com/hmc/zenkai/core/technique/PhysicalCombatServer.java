package com.hmc.zenkai.core.technique;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.core.ModGameRules;
import com.hmc.zenkai.core.config.StatsConfig;
import com.hmc.zenkai.core.mastery.MasteryEffects;
import com.hmc.zenkai.core.network.feature.combat.CombatModeServerState;
import com.hmc.zenkai.core.network.feature.player.PlayerLifeCycle;
import com.hmc.zenkai.core.network.feature.player.PlayerStatsAttachment;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Ejecución server de técnicas físicas: validación (modo combate, manos vacías, no
 * bloqueando/derribado, desbloqueada, cooldown, stamina) + efecto. El daño sale por el
 * pipeline normal (playerAttack) con dos puentes hacia CombatZenkaiHooks:
 *  - FIRING evita que la rama atacante recalcule el golpe como melee básico.
 *  - defenseScale: la defensa del rival escala con el dmgMult (regla unificada con el ki).
 *
 * Movimientos con DURACIÓN (tick propio, PlayerTickEvent.Post):
 *  - DASH_PUNCH: 8 ticks de embestida, golpea al que toque (una vez a cada uno);
 *    chocar contra pared lo corta.
 *  - BARRAGE: 6 golpes, uno cada 2 ticks (12 ticks = 0.6 s), cono frontal por pulso.
 * Morir, caer derribado, bloquear o salir del modo combate cancela el movimiento activo.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID)
public final class PhysicalCombatServer {
    private PhysicalCombatServer() {}

    private static final int DASH_TICKS = 8;      // 0.4 s de trayecto
    private static final int BARRAGE_TICKS = 12;  // 6 pulsos (cada 2 ticks)
    private static final double DASH_SPEED = 1.1;

    /** Cooldowns por jugador+técnica (gameTime de disponibilidad). */
    private static final Map<UUID, long[]> COOLDOWNS = new HashMap<>();

    /** Movimiento con duración activo por jugador. */
    private static final Map<UUID, Active> ACTIVE = new HashMap<>();

    private static final class Active {
        final PhysicalTechnique tech;
        final double str;
        final Vec3 dir;
        final Set<Integer> hitIds = new HashSet<>();
        int ticksLeft;

        Active(PhysicalTechnique tech, double str, Vec3 dir, int ticks) {
            this.tech = tech;
            this.str = str;
            this.dir = dir;
            this.ticksLeft = ticks;
        }
    }

    // Puente con CombatZenkaiHooks (server thread único).
    private static boolean firing = false;
    private static double defenseScale = 1.0;
    public static boolean isFiring()           { return firing; }
    /** ¿Está en los i-frames de un movimiento? DASH_PUNCH no es solo un golpe: la embestida
     *  ES la esquiva, así que durante sus 8 ticks el jugador no recibe daño. */
    public static boolean hasIFrames(UUID id) {
        Active a = ACTIVE.get(id);
        return a != null && a.tech == PhysicalTechnique.DASH_PUNCH && a.ticksLeft > 0;
    }
    public static double currentDefenseScale() { return defenseScale; }

    public static void tryExecute(ServerPlayer sp, PhysicalTechnique t) {
        PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
        if (!att.isCombatActive() || !CombatModeServerState.isActive(sp.getUUID())) return;
        if (KiCombatServer.isBlocking(sp) || att.flags().isDowned()) return;
        if (!sp.getMainHandItem().isEmpty() || !sp.getOffhandItem().isEmpty()) return;
        if (!att.techniques().isUnlocked(t)) return;
        if (!t.enabled()) return; // sin JSON: técnica desactivada
        if (ACTIVE.containsKey(sp.getUUID())) return; // un movimiento a la vez

        long now = sp.level().getGameTime();
        long[] cds = COOLDOWNS.computeIfAbsent(sp.getUUID(),
                k -> new long[PhysicalTechnique.values().length]);
        if (now < cds[t.ordinal()]) return;

        int cost = (int) Math.ceil(att.getStaminaMax() * t.staminaPct()
                * MasteryEffects.techCostFactor(att, t.name()));
        if (att.getStamina() < cost) return;

        att.consumeStamina(cost);
        cds[t.ordinal()] = now + t.cooldownTicks();
        att.addTechniqueMastery(t.name(), (float) StatsConfig.techMasteryPerUse());

        double str = att.computeMeleeFinal()
                * MasteryEffects.formStatFactor(sp)
                * MasteryEffects.techDamageFactor(att, t.name());
        Vec3 look = sp.getLookAngle();

        switch (t) {
            case DASH_PUNCH -> {
                dashImpulse(sp, look);
                ACTIVE.put(sp.getUUID(), new Active(t, str, look, DASH_TICKS));
            }
            case BARRAGE -> ACTIVE.put(sp.getUUID(), new Active(t, str, look, BARRAGE_TICKS));
            case HEAVY_BLOW -> heavyBlow(sp, str, t);
            case KIAI -> kiai(sp, str, t);
        }
        PlayerLifeCycle.sync(sp);
    }

    /** Tick de los movimientos con duración. */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        Active a = ACTIVE.get(sp.getUUID());
        if (a == null) return;

        PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
        boolean cancel = !sp.isAlive() || att.flags().isDowned()
                || KiCombatServer.isBlocking(sp) || !CombatModeServerState.isActive(sp.getUUID());
        if (cancel) {
            ACTIVE.remove(sp.getUUID());
            return;
        }

        a.ticksLeft--;
        switch (a.tech) {
            case DASH_PUNCH -> {
                // Mantener velocidad el trayecto y golpear al que toque.
                sp.setDeltaMovement(a.dir.x * DASH_SPEED,
                        Math.max(sp.getDeltaMovement().y, a.dir.y * DASH_SPEED * 0.4),
                        a.dir.z * DASH_SPEED);
                sp.hurtMarked = true;
                if (damageEnabled(sp)) {
                    hitAll(sp, a, sp.getBoundingBox().inflate(1.2), a.str * a.tech.dmgMult());
                }
                if (sp.horizontalCollision) a.ticksLeft = 0; // pared: fin del trayecto
            }
            case BARRAGE -> {
                if (a.ticksLeft % 2 == 0 && damageEnabled(sp)) {
                    // Un pulso: cono frontal (los objetivos pueden recibir varios pulsos).
                    pulse(sp, a.str * a.tech.dmgMult(), a.tech);
                }
            }
            default -> a.ticksLeft = 0; // no debería: los instantáneos no se registran
        }
        if (a.ticksLeft <= 0) ACTIVE.remove(sp.getUUID());
    }

    // ── Efectos ──────────────────────────────────────────────────────────────

    private static void dashImpulse(ServerPlayer sp, Vec3 look) {
        sp.setDeltaMovement(look.x * 1.6, Math.min(look.y * 1.6, 0.3) + 0.1, look.z * 1.6);
        sp.hurtMarked = true;
    }

    /** Golpe único pesado: daño + EXPLOSIÓN al impactar (partículas + AoE 50% con caída
     *  + knockback). Sin romper bloques: es físico, no ki. */
    /** Golpe único pesado: SIEMPRE se ejecuta. Con objetivo directo: daño completo +
     *  knockback en él. Sin objetivo: el golpe revienta donde termina la trayectoria
     *  (bloque mirado o aire a rango máximo). En ambos casos: explosión (partículas +
     *  sonido + AoE 50% con caída + empuje radial). Sin romper bloques: físico, no ki. */
    private static void heavyBlow(ServerPlayer sp, double str, PhysicalTechnique t) {
        double dmg = str * t.dmgMult();
        Vec3 look = sp.getLookAngle();
        LivingEntity target = firstInRay(sp, t.range(), 1.0);

        Vec3 c;
        if (target != null) {
            c = target.position().add(0, target.getBbHeight() * 0.5, 0);
        } else {
            // Punto de impacto: el bloque en la mirada, o el aire a rango máximo.
            Vec3 eye = sp.getEyePosition();
            Vec3 end = eye.add(look.scale(t.range()));
            var clip = sp.level().clip(new net.minecraft.world.level.ClipContext(
                    eye, end,
                    net.minecraft.world.level.ClipContext.Block.COLLIDER,
                    net.minecraft.world.level.ClipContext.Fluid.NONE, sp));
            c = clip.getType() != net.minecraft.world.phys.HitResult.Type.MISS
                    ? clip.getLocation() : end;
        }

        withBridge(t.dmgMult(), () -> {
            if (target != null) {
                if (damageEnabled(sp)) {
                    target.hurt(sp.damageSources().playerAttack(sp), (float) dmg);
                }
                target.knockback(1.8, -look.x, -look.z);
            }

            // Onda expansiva: 50% con caída lineal, radio 2.5, empuje radial. Siempre.
            double radius = 2.5;
            for (LivingEntity e : sp.serverLevel().getEntitiesOfClass(LivingEntity.class,
                    AABB.ofSize(c, radius * 2, radius * 2, radius * 2),
                    x -> x != sp && x != target && x.isAlive())) {
                double dist = e.position().add(0, e.getBbHeight() * 0.5, 0).distanceTo(c);
                if (dist > radius) continue;
                double falloff = 1.0 - dist / radius;
                if (damageEnabled(sp)) {
                    e.hurt(sp.damageSources().playerAttack(sp), (float) (dmg * 0.5 * falloff));
                }
                Vec3 away = e.position().subtract(c).normalize();
                e.setDeltaMovement(e.getDeltaMovement().add(away.x * 0.8, 0.25, away.z * 0.8));
                e.hurtMarked = true;
            }
        });

        sp.serverLevel().sendParticles(ParticleTypes.EXPLOSION, c.x, c.y, c.z, 1, 0, 0, 0, 0);
        sp.serverLevel().playSound(null, c.x, c.y, c.z,
                SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.0f, 1.2f);
    }

    /** Kiai: cono frontal con daño moderado + empujón ESCALADO CON STR (softcap: 0.7 débiles
     *  -> ~1.6 fuerte); desvía proyectiles ki ajenos. El empuje se aplica DESPUÉS del daño
     *  para que no lo pise el knockback vanilla de hurt(). */
    private static void kiai(ServerPlayer sp, double str, PhysicalTechnique t) {
        double push = 0.7 + 0.9 * (str / (str + 100.0)); // 100 = perilla de "STR media"
        double dmg = str * t.dmgMult();
        Vec3 look = sp.getLookAngle();
        List<LivingEntity> targets = inCone(sp, t.range(), 0.4);

        if (dmg > 0.0 && damageEnabled(sp)) {
            withBridge(t.dmgMult(), () -> {
                for (LivingEntity e : targets) {
                    e.hurt(sp.damageSources().playerAttack(sp), (float) dmg);
                }
            });
        }

        for (LivingEntity e : targets) {
            e.setDeltaMovement(e.getDeltaMovement()
                    .add(look.x * push, 0.3 + push * 0.15, look.z * push));
            e.hurtMarked = true;
        }

        AABB box = sp.getBoundingBox().inflate(t.range());
        for (var proj : sp.serverLevel().getEntitiesOfClass(
                com.hmc.zenkai.content.entity.technique.KiProjectileEntity.class, box,
                pr -> pr.getOwner() != sp && pr.canBeDeflected())) {
            double speed = proj.getDeltaMovement().length();
            if (!proj.deflect(sp)) continue; // guarda doble: barreras y proyectiles sin daño
            proj.setDeltaMovement(look.scale(Math.max(speed, 0.4 + push * 0.4)));
        }
    }

    /** Pulso de barrage: cono frontal corto. Resetea i-frames: la ráfaga define su propio
     *  ritmo (6 golpes/0.6 s), no la ventana de invulnerabilidad vanilla. */
    private static void pulse(ServerPlayer sp, double dmgPerHit, PhysicalTechnique t) {
        withBridge(t.dmgMult(), () -> {
            for (LivingEntity e : inCone(sp, t.range(), 0.5)) {
                e.invulnerableTime = 0;
                e.hurt(sp.damageSources().playerAttack(sp), (float) dmgPerHit);
            }
        });
    }

    /** Daña una vez a cada entidad nueva dentro de la caja (dash). */
    private static void hitAll(ServerPlayer sp, Active a, AABB box, double dmg) {
        withBridge(a.tech.dmgMult(), () -> {
            for (LivingEntity e : sp.serverLevel().getEntitiesOfClass(LivingEntity.class, box,
                    x -> x != sp && x.isAlive() && !a.hitIds.contains(x.getId()))) {
                a.hitIds.add(e.getId());
                e.hurt(sp.damageSources().playerAttack(sp), (float) dmg);
            }
        });
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** Ejecuta el daño con el puente hacia CombatZenkaiHooks activado. */
    private static void withBridge(double dmgMult, Runnable r) {
        firing = true;
        defenseScale = Math.max(0.1, dmgMult);
        try {
            r.run();
        } finally {
            firing = false;
            defenseScale = 1.0;
        }
    }

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

    /** LivingEntity en cono frontal (dot(mirada, dirección) > minDot). */
    private static List<LivingEntity> inCone(ServerPlayer sp, double range, double minDot) {
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

    public static void clear(UUID id) {
        COOLDOWNS.remove(id);
        ACTIVE.remove(id);
    }


}