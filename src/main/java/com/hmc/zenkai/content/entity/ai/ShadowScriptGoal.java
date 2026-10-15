package com.hmc.zenkai.content.entity.ai;

import com.hmc.zenkai.content.entity.misc.ShadowCloneEntity;
import com.hmc.zenkai.content.entity.misc.ShadowDebugMode;
import com.hmc.zenkai.content.entity.technique.KiProjectileEntity;
import com.hmc.zenkai.feature.combat.ZenkaiStats;
import com.hmc.zenkai.feature.combat.entity.EntityStats;
import com.hmc.zenkai.feature.technique.KiCombatServer;
import com.hmc.zenkai.feature.technique.KiTechniqueType;
import com.hmc.zenkai.feature.technique.TechniqueEffect;
import com.hmc.zenkai.registry.ModEntities;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Ejecuta el guion de debug de la Sombra ("/zenkai debug dummy"): quedarse quieta, spamear una
 * técnica de ki, mantener la guardia, seguir al dueño o huir de él. Prioridad 0 con MOVE y LOOK,
 * por delante de toda la IA normal; el cuerpo a cuerpo y las físicas NO pasan por aquí (se
 * dejan a sus goals de siempre y se apagan los demás con combatGoalAllowed).
 *
 * El objetivo es siempre el dueño (quien creó el muñeco), esté o no a la vista, y el disparo
 * ignora los cooldowns del kit: solo manda el intervalo pedido. Es una herramienta de pruebas,
 * no una IA: no persiste entre reinicios (la Sombra vuelve a ser la neutral de siempre).
 */
public class ShadowScriptGoal extends Goal {

    private static final double FOLLOW_DISTANCE = 4.0;
    private static final double FLEE_TRIGGER = 12.0;

    private final ShadowCloneEntity mob;
    private int ticks;

    public ShadowScriptGoal(ShadowCloneEntity mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        ShadowDebugMode m = mob.debugMode();
        return m != null && m.scripted();
    }

    @Override
    public boolean canContinueToUse() { return canUse(); }

    @Override
    public void start() { ticks = 0; }

    @Override
    public void stop() {
        mob.setBlocking(false);
        mob.setAttackPose(PosedAttacker.POSE_NONE);
    }

    @Override
    public boolean requiresUpdateEveryTick() { return true; }

    @Override
    public void tick() {
        ShadowDebugMode mode = mob.debugMode();
        LivingEntity target = mob.debugTarget();
        if (mode == null || target == null) return;

        mob.getLookControl().setLookAt(target, 30f, 30f);
        double dist = mob.distanceTo(target);

        // La guardia solo se mantiene en BLOCK; en cualquier otro modo se suelta.
        mob.setBlocking(mode == ShadowDebugMode.BLOCK);

        switch (mode) {
            case FOLLOW -> {
                if (dist > FOLLOW_DISTANCE) mob.getNavigation().moveTo(target, 1.2);
                else mob.getNavigation().stop();
            }
            case FLEE -> {
                if (dist < FLEE_TRIGGER) {
                    Vec3 away = DefaultRandomPos.getPosAway(mob, 16, 7, target.position());
                    if (away != null) mob.getNavigation().moveTo(away.x, away.y, away.z, 1.3);
                } else {
                    mob.getNavigation().stop();
                }
            }
            case SPAM_KI -> {
                mob.getNavigation().stop();
                mob.setAttackPose(PosedAttacker.POSE_KI_RIGHT);
                // Intervalo 0 = el cooldown real de la técnica (el mismo que tendría un jugador).
                int interval = mob.debugKiInterval() > 0 ? mob.debugKiInterval()
                        // Ciclo completo de un jugador: el enfriamiento que queda al soltar + la carga (desde
                        // 2026-10-15 cooldownTicksFor ya descuenta la carga, ver su javadoc).
                        : Math.max(1, KiCombatServer.cooldownTicksFor(mob.debugKiType(), mob.debugKiSize())
                                + KiCombatServer.chargeTicksFor(mob.debugKiType(), mob.debugKiSize()));
                if (++ticks >= interval) {
                    ticks = 0;
                    fireKi(target);
                }
            }
            default -> mob.getNavigation().stop();
        }
    }

    /** Dispara como lo haría un jugador: el MISMO KiProjectileEntity, con el daño de la fórmula
     *  normal a partir del WIL de la Sombra. Se apunta al centro del objetivo. */
    private void fireKi(LivingEntity target) {
        if (mob.level().isClientSide()) return;
        KiTechniqueType type = mob.debugKiType();
        int size = mob.debugKiSize();

        EntityStats stats = ZenkaiStats.entityStats(mob);
        double kiPower = stats == null ? 0.0 : stats.computeKiPowerFinal();
        double damage = KiCombatServer.computeDamage(kiPower, type, size);

        mob.lookAt(EntityAnchorArgument.Anchor.EYES, target.getEyePosition());
        mob.triggerAnim("KiAttack", "ki_shoot");

        Vec3 eye = mob.getEyePosition();
        Vec3 dir = target.getBoundingBox().getCenter().subtract(eye).normalize();
        KiProjectileEntity proj = new KiProjectileEntity(ModEntities.KI_PROJECTILE.get(), mob.level());
        proj.configure(mob, type, type.defaultRgb(), size, damage, 100, TechniqueEffect.NONE);
        Vec3 spawn = eye.add(dir.scale(0.9));
        proj.setPos(spawn.x, spawn.y, spawn.z);
        proj.setDeltaMovement(dir.scale(type.speed()));
        mob.level().addFreshEntity(proj);
    }
}
