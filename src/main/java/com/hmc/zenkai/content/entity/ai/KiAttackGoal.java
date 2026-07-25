package com.hmc.zenkai.content.entity.ai;

import com.hmc.zenkai.registry.ModEntities;
import com.hmc.zenkai.content.entity.technique.KiProjectileEntity;
import com.hmc.zenkai.feature.combat.entity.EntityKiAttack;
import com.hmc.zenkai.feature.combat.entity.EntityStats;
import com.hmc.zenkai.feature.combat.ZenkaiStats;
import com.hmc.zenkai.feature.technique.KiCombatServer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * Hace que un mob con ki_attacks en su JSON dispare técnicas. Elige AL AZAR entre las que
 * están listas de cooldown, mira al objetivo, hace un wind-up con animación y suelta el
 * proyectil — el MISMO KiProjectileEntity que dispara un jugador, con el daño calculado por
 * la misma fórmula (KiCombatServer.computeDamage) con el WIL del mob.
 *
 * Cooldown POR ataque, guardado aquí en gameTime: dos técnicas distintas alternan solas.
 * El goal solo se añade si la entidad tiene ataques; el saibaman (melee) nunca lo lleva.
 */
public class KiAttackGoal<T extends Mob & GeoEntity> extends Goal {

    private final T mob;
    private final List<EntityKiAttack> attacks;
    private final double moveSpeed;

    /** ready-at por índice de ataque (gameTime). Paralelo a 'attacks'. */
    private final long[] readyAt;

    private EntityKiAttack chosen;
    private int windup;          // ticks restantes de wind-up antes de soltar
    private LivingEntity target;

    /** Ticks de carga antes de disparar. Da tiempo a que se vea la animación. */
    private static final int WINDUP_TICKS = 20;

    public KiAttackGoal(T mob, List<EntityKiAttack> attacks, double moveSpeed) {
        this.mob = mob;
        this.attacks = attacks;
        this.moveSpeed = moveSpeed;
        this.readyAt = new long[attacks.size()];
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity tgt = mob.getTarget();
        if (tgt == null || !tgt.isAlive()) return false;

        // ¿Hay algún ataque listo cuyo rango alcance y con línea de visión?
        EntityKiAttack pick = pickReadyInRange(tgt);
        if (pick == null) return false;

        this.target = tgt;
        this.chosen = pick;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return windup > 0 && target != null && target.isAlive()
                && mob.getSensing().hasLineOfSight(target);
    }

    @Override
    public void start() {
        windup = WINDUP_TICKS;
        mob.getNavigation().stop();
        // Animación de carga. triggerAnim reinicia aunque sea la misma: no se queda pegada.
        triggerSafe("ki_charge");
    }

    @Override
    public void stop() {
        chosen = null;
        target = null;
        windup = 0;
    }

    @Override
    public boolean requiresUpdateEveryTick() { return true; }

    @Override
    public void tick() {
        if (target == null || chosen == null) return;

        mob.getLookControl().setLookAt(target, 30f, 30f);

        // Mantenerse a distancia: si el objetivo se acerca demasiado, no perseguir (es a distancia).
        double dist = mob.distanceTo(target);
        if (dist > chosen.range()) {
            mob.getNavigation().moveTo(target, moveSpeed);
        } else {
            mob.getNavigation().stop();
        }

        if (--windup > 0) return;

        fire();
        // Marca el cooldown de ESTE ataque y termina; canUse volverá a elegir cuando toque.
        int idx = attacks.indexOf(chosen);
        if (idx >= 0) readyAt[idx] = mob.level().getGameTime() + chosen.cooldownTicks();
        windup = 0;
    }

    /** Suelta el proyectil, idéntico a un disparo de jugador. Solo servidor. */
    private void fire() {
        if (mob.level().isClientSide()) return;

        triggerSafe("ki_shoot");

        double kiPower = kiPowerOf(mob);
        double damage = KiCombatServer.computeDamage(kiPower, chosen.type(), chosen.size())
                * chosen.damageMult();

        Vec3 eye = mob.getEyePosition();
        Vec3 dir = target.getEyePosition().subtract(eye).normalize();

        KiProjectileEntity proj =
                new KiProjectileEntity(ModEntities.KI_PROJECTILE.get(), mob.level());
        proj.configure(mob, chosen.type(), chosen.rgb(), chosen.size(),
                damage, 100, false); // los mobs no lanzan versión explosiva (por ahora)
        Vec3 spawn = eye.add(dir.scale(0.9));
        proj.setPos(spawn.x, spawn.y, spawn.z);
        proj.setDeltaMovement(dir.scale(chosen.type().speed()));
        mob.level().addFreshEntity(proj);
    }

    /** WIL del mob si tiene stats resueltos; si no, 0 (el daño saldrá mínimo pero no revienta). */
    private static double kiPowerOf(Mob mob) {
        EntityStats stats = ZenkaiStats.entityStats(mob);
        return stats == null ? 0.0 : stats.computeKiPowerFinal();
    }

    /** Un ataque al azar entre los listos, en rango y con visión. null si ninguno. */
    private EntityKiAttack pickReadyInRange(LivingEntity tgt) {
        long now = mob.level().getGameTime();
        double dist = mob.distanceTo(tgt);
        if (!mob.getSensing().hasLineOfSight(tgt)) return null;

        List<EntityKiAttack> ready = new ArrayList<>();
        for (int i = 0; i < attacks.size(); i++) {
            EntityKiAttack a = attacks.get(i);
            if (now >= readyAt[i] && dist <= a.range()) ready.add(a);
        }
        if (ready.isEmpty()) return null;
        return ready.get(mob.getRandom().nextInt(ready.size()));
    }

    /** triggerAnim viene de GeoEntity, garantizado por el bound del genérico. */
    private void triggerSafe(String anim) {
        mob.triggerAnim("KiAttack", anim); // ⚠ (controllerName, animName) GeckoLib 4.x
    }
}