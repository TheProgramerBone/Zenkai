package com.hmc.zenkai.content.entity.ai;

import com.hmc.zenkai.content.entity.ZenkaiDefaultMob;
import com.hmc.zenkai.feature.technique.KiTechniqueType;
import com.hmc.zenkai.feature.technique.TechniqueEffect;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 *
 * minRange (opcional, 0 = sin mínimo): por debajo de esta distancia el goal ni se ofrece —
 * pensado para mobs que TAMBIÉN llevan PhysicalAttackGoal, así el cuerpo a cuerpo no compite con
 * el ki por los mismos flags cuando el objetivo ya está encima (ver el javadoc de
 * ShadowCloneEntity: con 6 ki_attacks de cooldown corto casi siempre había uno listo, así que
 * Ki ganaba SIEMPRE la prioridad y Físicas/Físicas nunca llegaban a correr). También se suprime
 * si el objetivo está EN EL AIRE y cerca (ver CombatAiUtil.isAirborne) — un mob que puede volar
 * gana más cerrando distancia para pegar que gastando ki, pedido explícito del usuario.
 *
 * SELECCIÓN entre los ataques listos (pickReadyInRange) ya NO es puro azar: ver el javadoc de
 * pickReadyInRange para el orden de criterios (guardia del objetivo, rematador a poca vida,
 * anti-repetición, y solo entonces azar entre lo que quede).
 */
public class KiAttackGoal<T extends ZenkaiDefaultMob> extends Goal {

    private static final Logger LOGGER = LoggerFactory.getLogger("Zenkai-CombatAI");

    private final T mob;
    private final List<EntityKiAttack> attacks;
    private final double moveSpeed;
    private final double minRange;

    /** ready-at por índice de ataque (gameTime). Paralelo a 'attacks'. */
    private final long[] readyAt;

    private EntityKiAttack chosen;
    private int windup;          // ticks restantes de wind-up antes de soltar
    private LivingEntity target;

    /** Throttle del log de diagnóstico en canUse() — sin esto spamearía cada tick que el goal
     *  no esté corriendo (GoalSelector llama canUse() de cada goal parado en cada tick). */
    private long lastDiagLog = Long.MIN_VALUE;
    private static final int DIAG_LOG_INTERVAL_TICKS = 100; // ~5s

    /** Ticks de carga antes de disparar. Da tiempo a que se vea la animación. */
    private static final int WINDUP_TICKS = 20;

    /** Respiro compartido con PhysicalAttackGoal (y con ZenkaiMeleeAttackGoal, que también lo
     *  respeta) tras CUALQUIER ataque — ver ZenkaiDefaultMob.markActionUsed/isActionOnCooldown.
     *  Subido de 15 a 30 tras que el usuario reportara que 15 (0.75 s) no se notaba como pausa
     *  real en juego — 30 (1.5 s) es lo bastante largo para verse como un respiro deliberado sin
     *  volver al mob pasivo. */
    private static final int ACTION_COOLDOWN_TICKS = 30;

    /** Por debajo de este % de vida del objetivo, se prioriza el ataque de mayor daño estimado
     *  entre los listos en vez de tratarlo como una opción más del azar — ver pickReadyInRange. */
    private static final double FINISHER_HEALTH_THRESHOLD = 0.25;

    /** Último tipo disparado — para no repetirlo si hay alternativas listas (paso 3 de
     *  pickReadyInRange). null = todavía no ha disparado nada. */
    private KiTechniqueType lastFired;

    public KiAttackGoal(T mob, List<EntityKiAttack> attacks, double moveSpeed) {
        this(mob, attacks, moveSpeed, 0.0);
    }

    /** No dejar que PhysicalAttackGoal (menor prioridad) desaloje un wind-up YA empezado — de por
     *  sí no podría (solo un goal de MAYOR prioridad puede preemptar, ver
     *  WrappedGoal.canBeReplacedBy), pero se declara igual de explícito por simetría con
     *  PhysicalAttackGoal.isInterruptable() y por si algún día otro goal de prioridad aún mayor
     *  se añade delante de Ki. Ver su javadoc para el bug real que esto cierra (persecución
     *  errática/elecciones de ataque sin sentido con un objetivo volando cerca del umbral de
     *  distancia). */
    @Override
    public boolean isInterruptable() {
        return windup <= 0;
    }

    public KiAttackGoal(T mob, List<EntityKiAttack> attacks, double moveSpeed, double minRange) {
        this.mob = mob;
        this.attacks = attacks;
        this.moveSpeed = moveSpeed;
        this.minRange = minRange;
        this.readyAt = new long[attacks.size()];
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity tgt = mob.getTarget();
        if (tgt == null || !tgt.isAlive()) return false;
        if (mob instanceof BlockingMob bm && bm.isBlockingNow()) return false;
        if (mob.isActionOnCooldown()) return false; // respiro anti-spam, ver ZenkaiDefaultMob

        if (minRange > 0 && mob.distanceTo(tgt) <= minRange) {
            logDiag(tgt, "demasiado cerca (< minRange=" + minRange + "), que ataque el físico");
            return false;
        }

        // Objetivo en el aire Y cerca (mismo umbral que minRange): mejor cerrar y golpear que
        // gastar ki, si este mob puede volar de verdad — ver el javadoc de clase. Un mob SIN
        // vuelo no puede alcanzar a un objetivo en el aire por muy cerca que esté, así que para
        // él la regla no aplica (seguiría sin poder pegarle) y se deja el ki como única opción.
        if (minRange > 0 && mob.canFlyNow() && CombatAiUtil.isAirborne(tgt) && mob.distanceTo(tgt) <= minRange) {
            logDiag(tgt, "objetivo en el aire y cerca, que ataque el físico (puedo volar)");
            return false;
        }

        // ¿Hay algún ataque listo cuyo rango alcance y con línea de visión?
        EntityKiAttack pick = pickReadyInRange(tgt);
        if (pick == null) {
            logDiag(tgt, "sin ataque listo en rango/LOS");
            return false;
        }

        this.target = tgt;
        this.chosen = pick;
        return true;
    }

    /** Throttled a una vez cada DIAG_LOG_INTERVAL_TICKS. Quitar cuando se confirme en juego. */
    private void logDiag(LivingEntity tgt, String reason) {
        long now = mob.level().getGameTime();
        if (now - lastDiagLog < DIAG_LOG_INTERVAL_TICKS) return;
        lastDiagLog = now;
        double dist = mob.distanceTo(tgt);
        boolean los = mob.getSensing().hasLineOfSight(tgt);
        LOGGER.info("[Zenkai] {} (ki) no ataca ({}): dist={} LOS={} readyAt={}",
                mob, reason, String.format("%.1f", dist), los, java.util.Arrays.toString(readyAt));
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
        mob.setWindingUp(true); // AttackTelegraph — BlockHabitGoal del objetivo lo lee para bloquear en reacción
        // Ver el mismo fix/comentario en PhysicalAttackGoal.start(): encara el CUERPO de golpe,
        // yBodyRot no sigue a LookControl al instante por su cuenta.
        mob.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, target.getEyePosition());
        LOGGER.info("[Zenkai] {} (ki) empieza a cargar {} contra {}", mob, chosen.type(), target);
        // Animación de carga. triggerAnim reinicia aunque sea la misma: no se queda pegada.
        triggerSafe("ki_charge");
        // Para mobs sin GeckoLib de verdad (la Sombra, PlayerModel real) triggerSafe no se ve —
        // ver el javadoc de PosedAttacker. Un solo brazo (izquierdo o derecho al azar), no los
        // dos — pedido explícito: se ve mejor que la postura de "cargar con las dos manos".
        if (mob instanceof PosedAttacker posed) {
            posed.setAttackPose(mob.getRandom().nextBoolean()
                    ? PosedAttacker.POSE_KI_LEFT : PosedAttacker.POSE_KI_RIGHT);
        }
    }

    @Override
    public void stop() {
        // Distingue "se completó" (windup llegó a 0, fire() ya corrió) de "lo interrumpieron"
        // (otro goal de mejor prioridad se lo quitó a mitad de carga) — justo lo que se
        // sospecha que pasaba con MeleeAttackGoal antes de reordenar prioridades.
        if (windup > 0) {
            LOGGER.info("[Zenkai] {} (ki) INTERRUMPIDO a mitad de carga (quedaban {} ticks)", mob, windup);
        }
        chosen = null;
        target = null;
        windup = 0;
        mob.setWindingUp(false);
        if (mob instanceof PosedAttacker posed) posed.setAttackPose(PosedAttacker.POSE_NONE);
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
        lastFired = chosen.type();
        mob.markActionUsed(ACTION_COOLDOWN_TICKS); // respiro compartido con PhysicalAttackGoal
        windup = 0;
    }

    /** Suelta el proyectil, idéntico a un disparo de jugador. Solo servidor. */
    private void fire() {
        if (mob.level().isClientSide()) return;

        triggerSafe("ki_shoot");

        double kiPower = kiPowerOf(mob);
        double damage = KiCombatServer.computeDamage(kiPower, chosen.type(), chosen.size())
                * chosen.damageMult();
        LOGGER.info("[Zenkai] {} (ki) DISPARA {} daño={} kiPower={}",
                mob, chosen.type(), String.format("%.1f", damage), String.format("%.1f", kiPower));

        Vec3 eye = mob.getEyePosition();
        Vec3 dir = target.getEyePosition().subtract(eye).normalize();

        KiProjectileEntity proj =
                new KiProjectileEntity(ModEntities.KI_PROJECTILE.get(), mob.level());
        proj.configure(mob, chosen.type(), chosen.rgb(), chosen.size(),
                damage, 100, TechniqueEffect.NONE); // los mobs no rompen terreno (por ahora)
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

    /** Entre los ataques listos, en rango y con visión, elige con criterio — ya NO puro azar:
     *  1) Si el objetivo está bloqueando (CombatAiUtil.isBlocking), prefiere los que tengan más
     *     opciones de superar guardia: varios proyectiles (count() &gt; 1, p. ej. BURST) o AoE
     *     alto (aoeFactor()). Si ese subconjunto queda vacío, se usa el pool completo — nunca
     *     bloquea la decisión.
     *  2) Si el objetivo está por debajo de FINISHER_HEALTH_THRESHOLD de vida, prioriza el de
     *     mayor daño estimado (mismo cálculo que fire()) como rematador, en vez de tratarlo como
     *     una opción más del azar.
     *  3) Si queda más de una opción, excluye lastFired (el último tipo disparado) — anti-spam,
     *     ver el pendiente de "el ciclo ki/físicas turnándose demasiado rápido".
     *  4) Azar entre lo que quede — mantiene variabilidad, no lo vuelve robótico.
     *  null si ninguno está listo/en rango/con visión. */
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

        List<EntityKiAttack> pool = ready;

        if (CombatAiUtil.isBlocking(tgt)) {
            List<EntityKiAttack> guardBreakers = pool.stream()
                    .filter(a -> a.type().count() > 1 || a.type().aoeFactor() >= 0.5)
                    .toList();
            if (!guardBreakers.isEmpty()) pool = guardBreakers;
        }

        if (tgt.getHealth() / tgt.getMaxHealth() <= FINISHER_HEALTH_THRESHOLD) {
            EntityKiAttack finisher = pool.stream()
                    .max(java.util.Comparator.comparingDouble(a ->
                            KiCombatServer.computeDamage(kiPowerOf(mob), a.type(), a.size()) * a.damageMult()))
                    .orElse(null);
            if (finisher != null) return finisher;
        }

        if (pool.size() > 1 && lastFired != null) {
            List<EntityKiAttack> notRepeated = pool.stream().filter(a -> a.type() != lastFired).toList();
            if (!notRepeated.isEmpty()) pool = notRepeated;
        }

        return pool.get(mob.getRandom().nextInt(pool.size()));
    }

    /** triggerAnim viene de GeoEntity, garantizado por el bound del genérico. */
    private void triggerSafe(String anim) {
        mob.triggerAnim("KiAttack", anim); // ⚠ (controllerName, animName) GeckoLib 4.x
    }
}