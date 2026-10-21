package com.hmc.zenkai.content.entity.ai;

import com.hmc.zenkai.content.entity.ZenkaiDefaultMob;
import com.hmc.zenkai.feature.combat.ZenkaiStats;
import com.hmc.zenkai.feature.combat.entity.EntityStats;
import com.hmc.zenkai.feature.combat.entity.EntityTransformation;

import java.util.EnumSet;
import java.util.List;

/**
 * Transformación por vida de un NPC de combate ("transformations" de su ficha, ver
 * {@link EntityTransformation}). Prioridad 0 con MOVE+LOOK+JUMP: mientras dura, el mob no persigue ni
 * ataca. Solo EMPIEZA en combate (con objetivo) para que un mob herido que nadie persigue no se
 * transforme solo; y respeta la protección de los goals de ataque (no corta un wind-up en curso,
 * porque el selector no desaloja a un goal no interrumpible).
 * Las fases van en orden y cada una una sola vez (EntityStats.getStage() dice por cuál va). Si la vida
 * cae de golpe por debajo de varios umbrales, canUse() vuelve a ser true al acabar una y encadena la
 * siguiente. El PL sube en {@link #stop()} y no en {@link #start()}: así el aumento llega cuando la
 * animación acaba, y un mob que muere a mitad no se queda con el poder sin haberlo "ganado".
 */
public class TransformationGoal extends net.minecraft.world.entity.ai.goal.Goal {

    private final ZenkaiDefaultMob mob;
    private final List<EntityTransformation> stages;
    private EntityTransformation current;
    private long endsAt;
    /** El objetivo con el que empezó: si algo (un TargetGoal que dejó de verlo) lo borra a mitad, se devuelve al acabar. */
    private net.minecraft.world.entity.LivingEntity startTarget;

    public TransformationGoal(ZenkaiDefaultMob mob, List<EntityTransformation> stages) {
        this.mob = mob;
        this.stages = stages;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    /** La fase que toca ahora si ya se cumple su umbral de vida, o null. */
    private EntityTransformation pending() {
        if (mob.getTarget() == null || !mob.isAlive()) return null;
        EntityStats stats = ZenkaiStats.entityStats(mob);
        if (stats == null || stats.getStage() >= stages.size()) return null;
        EntityTransformation next = stages.get(stats.getStage());
        float frac = stats.getBodyMax() <= 0 ? 1f : stats.getBody() / (float) stats.getBodyMax();
        return frac * 100f <= next.atHealthPct() ? next : null;
    }

    @Override
    public boolean canUse() { return pending() != null; }

    @Override
    public boolean canContinueToUse() {
        return mob.isAlive() && mob.level().getGameTime() < endsAt;
    }

    @Override
    public boolean isInterruptable() { return false; }

    @Override
    public void start() {
        current = pending();
        endsAt = mob.level().getGameTime() + (current == null ? 0 : current.durationTicks());
        startTarget = mob.getTarget();
        mob.getNavigation().stop();
        mob.setTransforming(true);
    }

    @Override
    public void tick() {
        var target = mob.getTarget();
        if (target != null) mob.getLookControl().setLookAt(target, 30f, 30f);
    }

    @Override
    public void stop() {
        mob.setTransforming(false);
        if (mob.isAlive() && mob.getTarget() == null && startTarget != null && startTarget.isAlive()) {
            mob.setTarget(startTarget);
        }
        startTarget = null;
        if (!mob.isAlive() || current == null) return;
        EntityStats stats = ZenkaiStats.entityStats(mob);
        var statDef = mob.statDef();
        if (stats != null && statDef != null) stats.advanceStage(statDef, mob, current);
        current = null;
    }
}
