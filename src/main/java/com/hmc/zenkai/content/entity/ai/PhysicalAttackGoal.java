package com.hmc.zenkai.content.entity.ai;

import com.hmc.zenkai.content.entity.ZenkaiDefaultMob;
import com.hmc.zenkai.feature.combat.ZenkaiStats;
import com.hmc.zenkai.feature.combat.entity.EntityPhysicalAttack;
import com.hmc.zenkai.feature.combat.entity.EntityStats;
import com.hmc.zenkai.feature.technique.PhysicalCombatServer;
import com.hmc.zenkai.feature.technique.PhysicalTechnique;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumSet;
import java.util.List;

/**
 * Hace que un mob con physical_attacks en su JSON use técnicas físicas cuerpo a cuerpo — espejo
 * de {@link KiAttackGoal} pero SIN proyectil: el efecto (daño + empuje) se aplica directamente
 * sobre el objetivo cuando termina el wind-up. Reutiliza
 * {@link PhysicalCombatServer#computeDamage(double, com.hmc.zenkai.feature.technique.PhysicalTechnique, double)}
 * (la parte PURA de la fórmula, sin mastería ni bonus de Ki Fist — eso es exclusivo de
 * jugadores) con la fuerza (STR) del mob.
 *
 * SIMPLIFICACIÓN DELIBERADA frente al pipeline de ServerPlayer (PhysicalCombatServer.ACTIVE, que
 * lleva movimientos con duración de varios ticks reales): aquí las cuatro técnicas se resuelven
 * en un único impacto (o ráfaga instantánea, para BARRAGE) al terminar el wind-up, cada una con
 * su propio empuje/AoE. Es suficiente para que un mob "se sienta" usando la técnica sin duplicar
 * esa máquina de estados — ver el javadoc de computeDamage para el porqué de no tocarla.
 *
 * Cooldown POR ataque, guardado aquí en gameTime, igual que KiAttackGoal.
 *
 * engageRange (opcional, MAX_VALUE = sin tope): distancia máxima a la que el goal está
 * DISPUESTO A EMPEZAR a acercarse para golpear — no confundir con el "range" de cada técnica
 * (EntityPhysicalAttack.range, 3-4 bloques, el alcance real del golpe, usado en tick() para
 * decidir si ya hay que dejar de perseguir). Sin esta distinción, canUse() solo se activaba
 * cuando YA se estaba pegado al objetivo (dist &lt;= range de la técnica) — pero nada más traía
 * al mob hasta ahí en primer lugar (el wander idle no persigue), así que en la práctica esta
 * clase casi nunca llegaba a activarse por sí sola. Con engageRange, el goal se ofrece desde
 * más lejos y usa su propio wind-up para cerrar la distancia real.
 *
 * SELECCIÓN entre las listas de cooldown (pickReady) ya NO es puro azar: mismo orden de
 * criterios que KiAttackGoal.pickReadyInRange (guardia del objetivo, rematador a poca vida,
 * anti-repetición, y solo entonces azar entre lo que quede) — ver su javadoc para el detalle.
 */
public class PhysicalAttackGoal<T extends ZenkaiDefaultMob> extends Goal {

    private static final Logger LOGGER = LoggerFactory.getLogger("Zenkai-CombatAI");

    private final T mob;
    private final List<EntityPhysicalAttack> attacks;
    private final double moveSpeed;
    private final double engageRange;

    /** ready-at por índice de ataque (gameTime). Paralelo a 'attacks'. */
    private final long[] readyAt;

    private EntityPhysicalAttack chosen;
    private int windup;
    private LivingEntity target;

    /** Throttle del log de diagnóstico en canUse() — ver el mismo campo en KiAttackGoal. */
    private long lastDiagLog = Long.MIN_VALUE;
    private static final int DIAG_LOG_INTERVAL_TICKS = 100; // ~5s

    /** Golpes de BARRAGE, el conjunto entero en el mismo fire() — ver javadoc de clase. */
    private static final int BARRAGE_HITS = 3;

    /** Respiro compartido con KiAttackGoal (y con ZenkaiMeleeAttackGoal, que también lo respeta)
     *  tras CUALQUIER ataque — ver ZenkaiDefaultMob.markActionUsed/isActionOnCooldown. Mismo
     *  valor que KiAttackGoal, ver su javadoc para el porqué de 30 en vez de 15. */
    private static final int ACTION_COOLDOWN_TICKS = 30;

    /** Por debajo de este % de vida del objetivo, se prioriza la técnica de mayor daño estimado
     *  entre las listas en vez de tratarla como una opción más del azar — ver pickReady. */
    private static final double FINISHER_HEALTH_THRESHOLD = 0.25;

    /** Última técnica disparada — para no repetirla si hay alternativas listas. null = todavía
     *  no ha disparado nada. */
    private PhysicalTechnique lastFired;

    /** Distancia por debajo de la cual HEAVY_BLOW/BARRAGE son la opción "natural" (golpe directo
     *  sin necesitar cerrar hueco) — el mayor range() entre las entradas HEAVY_BLOW/BARRAGE del
     *  kit real, no un número inventado; con un kit sin ninguna de las dos, 3.0 de respaldo
     *  razonable. Calculado UNA vez en el constructor, ver pickByRange. */
    private final double closeRangeThreshold;

    /** Ticks de wind-up por técnica: {@link PhysicalTechnique#animTicks()}, ya data-driven desde
     *  el datapack (TechniqueDef, "anim_ticks") — server-safe, a diferencia del
     *  .animation.json de cliente que autora el clip real. Antes esto era un switch con
     *  constantes copiadas a mano de animation_length de cada clip ("si retocas el clip en
     *  Blockbench, actualiza esto también") — animTicks() ya es la fuente pensada exactamente
     *  para esto (ver su javadoc: "cuánto se mantiene el ActionState para que los demás vean la
     *  animación entera"), así que no hace falta duplicarla aquí. */
    private static int windupTicksFor(PhysicalTechnique t) {
        return t.animTicks();
    }

    public PhysicalAttackGoal(T mob, List<EntityPhysicalAttack> attacks, double moveSpeed) {
        this(mob, attacks, moveSpeed, Double.MAX_VALUE);
    }

    public PhysicalAttackGoal(T mob, List<EntityPhysicalAttack> attacks, double moveSpeed, double engageRange) {
        this.mob = mob;
        this.attacks = attacks;
        this.moveSpeed = moveSpeed;
        this.engageRange = engageRange;
        this.readyAt = new long[attacks.size()];
        this.closeRangeThreshold = attacks.stream()
                .filter(a -> a.type() == PhysicalTechnique.HEAVY_BLOW || a.type() == PhysicalTechnique.BARRAGE)
                .mapToDouble(EntityPhysicalAttack::range)
                .max().orElse(3.0);
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    /** No dejar que otro goal (Ki, de mayor prioridad) desaloje un wind-up YA empezado solo
     *  porque el objetivo cruzó un umbral de distancia a mitad de carga (p. ej. un jugador
     *  volando oscila unos bloques y KiAttackGoal.canUse() pasa a true) — reportado por el
     *  usuario como persecución errática y elecciones de ataque que no tenían sentido. Antes solo
     *  se protegía a Ki/Físicas frente a Melee (ver el javadoc de ShadowCloneEntity); esto cierra
     *  el mismo hueco entre Ki y Físicas, que sí comparten prioridad adyacente y SÍ pueden
     *  desalojarse entre sí sin este guard (WrappedGoal.canBeReplacedBy solo mira
     *  isInterruptable() + prioridad, no si ya está corriendo con inversión pendiente). */
    @Override
    public boolean isInterruptable() {
        return windup <= 0;
    }

    @Override
    public boolean canUse() {
        LivingEntity tgt = mob.getTarget();
        if (tgt == null || !tgt.isAlive()) return false;
        if (mob instanceof BlockingMob bm && bm.isBlockingNow()) return false;
        if (mob.isActionOnCooldown()) return false; // respiro anti-spam, ver ZenkaiDefaultMob

        if (mob.distanceTo(tgt) > engageRange) {
            logDiag(tgt, "fuera de engageRange=" + engageRange);
            return false;
        }

        // Solo cooldown aquí — el rango real de cada técnica lo gestiona tick() al acercarse.
        EntityPhysicalAttack pick = pickReady(tgt);
        if (pick == null) {
            logDiag(tgt, "ninguna lista (cooldown)");
            return false;
        }

        this.target = tgt;
        this.chosen = pick;
        return true;
    }

    /** Throttled a una vez cada DIAG_LOG_INTERVAL_TICKS — ver la misma función en KiAttackGoal.
     *  Quitar cuando se confirme en juego. */
    private void logDiag(LivingEntity tgt, String reason) {
        long now = mob.level().getGameTime();
        if (now - lastDiagLog < DIAG_LOG_INTERVAL_TICKS) return;
        lastDiagLog = now;
        double dist = mob.distanceTo(tgt);
        LOGGER.info("[Zenkai] {} (fisica) no ataca ({}): dist={} readyAt={}",
                mob, reason, String.format("%.1f", dist), java.util.Arrays.toString(readyAt));
    }

    @Override
    public boolean canContinueToUse() {
        return windup > 0 && target != null && target.isAlive();
    }

    @Override
    public void start() {
        windup = windupTicksFor(chosen.type());
        mob.setWindingUp(true); // AttackTelegraph — BlockHabitGoal del objetivo lo lee para bloquear en reacción
        // Encara el CUERPO al objetivo de golpe al empezar — no solo la cabeza (LookControl en
        // tick(), más abajo, ya la sigue, pero yBodyRot solo la alcanza gradualmente ~30%/tick,
        // ver LivingEntity.tickHeadTurn). Sin esto, una animación de técnica que llega justo
        // después de perseguir en otra dirección se reproduce con el cuerpo aún mirando a otro
        // lado — "la animación va hacia el jugador pero el cuerpo no", reportado por el usuario.
        // lookAt (a diferencia de setLookAt del LookControl) sincroniza yBodyRot=yHeadRot al
        // instante.
        mob.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, target.getEyePosition());
        LOGGER.info("[Zenkai] {} (fisica) empieza a cargar {} contra {}", mob, chosen.type(), target);
        // Trigger NATIVO de GeckoLib: el clip REAL de la técnica (zenkai.phys_*, copiado tal
        // cual de player_animations/phys_*.animation.json a zenkai_animations.animation.json —
        // ver ZenkaiDefaultMob.registerControllers/ZenkaiCommonAnimations) reproducido por el
        // motor de la librería, no un sampler propio a mano.
        triggerSafe(switch (chosen.type()) {
            case DASH_PUNCH -> "phys_dash_punch";
            case HEAVY_BLOW -> "phys_heavy_blow";
            case BARRAGE    -> "phys_barrage";
            case KIAI       -> "phys_kiai";
        });
    }

    @Override
    public void stop() {
        // Ver el comentario del mismo chequeo en KiAttackGoal.stop().
        if (windup > 0) {
            LOGGER.info("[Zenkai] {} (fisica) INTERRUMPIDO a mitad de carga (quedaban {} ticks)", mob, windup);
        }
        chosen = null;
        target = null;
        windup = 0;
        mob.setWindingUp(false);
    }

    @Override
    public boolean requiresUpdateEveryTick() { return true; }

    @Override
    public void tick() {
        if (target == null || chosen == null) return;

        mob.getLookControl().setLookAt(target, 30f, 30f);

        double dist = mob.distanceTo(target);
        if (dist > chosen.range()) {
            mob.getNavigation().moveTo(target, moveSpeed);
        } else {
            mob.getNavigation().stop();
        }

        if (--windup > 0) return;

        fire();
        int idx = attacks.indexOf(chosen);
        if (idx >= 0) readyAt[idx] = mob.level().getGameTime() + chosen.cooldownTicks();
        lastFired = chosen.type();
        mob.markActionUsed(ACTION_COOLDOWN_TICKS); // respiro compartido con KiAttackGoal
        windup = 0;
    }

    /** Aplica el efecto sobre el objetivo (y alrededores, según la técnica). Solo servidor. */
    private void fire() {
        if (mob.level().isClientSide()) return;

        // Con engageRange permitiendo empezar a acercarse desde mucho más lejos que el range()
        // real de la técnica, el wind-up (12 ticks) no siempre alcanza para cerrar la distancia
        // por completo — sin este guard, golpearía "por control remoto" desde varios bloques.
        // Margen de +2 sobre el range() nominal: tolera un ligero desajuste sin ser tan laxo
        // como para que parezca a distancia.
        double dist = mob.distanceTo(target);
        if (dist > chosen.range() + 2.0) {
            LOGGER.info("[Zenkai] {} (fisica) {} no conectó: dist={} > rango={}",
                    mob, chosen.type(), String.format("%.1f", dist), chosen.range());
            return;
        }

        // NO se dispara "strike" aquí: la técnica ya tiene su propio clip real disparado en
        // start() (phys_dash_punch/heavy_blow/barrage/kiai) que cubre todo el wind-up Y el
        // impacto — relanzar "strike" en este instante (mismo controlador "KiAttack") lo
        // INTERRUMPÍA a media animación justo en el golpe, un bug real heredado de antes de que
        // las físicas tuvieran animación propia (cuando esto era inerte para PlayerModel y no se
        // notaba). Confirmado como causa de parte de la rareza visual reportada por el usuario.

        double str = strengthOf(mob);
        LOGGER.info("[Zenkai] {} (fisica) GOLPEA {} str={}", mob, chosen.type(), String.format("%.1f", str));
        switch (chosen.type()) {
            case DASH_PUNCH -> {
                dashTowards(target);
                hit(target, str, 1.0, 1.6);
            }
            case HEAVY_BLOW -> {
                hit(target, str, 1.0, 2.2);
                // excludeTarget=true: target ya recibió el golpe directo de arriba, esto es SOLO
                // el remate alrededor — sin excluirlo, target se llevaría el golpe Y la onda.
                areaBurst(target.position(), 2.5, str * 0.5, true);
            }
            case BARRAGE -> {
                for (int i = 0; i < BARRAGE_HITS; i++) hit(target, str, 1.0 / BARRAGE_HITS, 0.5);
            }
            case KIAI ->
                // excludeTarget=false: a diferencia de HEAVY_BLOW, este ES el único daño de
                // KIAI — excluir a target (bug real encontrado: la llamada original SIEMPRE lo
                // excluía, así que KIAI nunca golpeaba a su propio objetivo si era el único
                // cerca) lo dejaría sin efecto contra la razón por la que se elige (contrarrestar
                // guardia/ki del objetivo, ver KiAttackGoal/PhysicalAttackGoal.pickReady).
                areaBurst(mob.position(), chosen.range(), str, false);
        }
    }

    /** Un golpe directo, con empuje alejando al objetivo del mob. dmgFraction reparte el
     *  damageMult de la entrada entre varios golpes (BARRAGE) sin tocar el JSON. Sonido:
     *  PhysicalCombatServer.playImpactSound, el MISMO sistema (PHYSICAL_IMPACT/_BLOCK, volumen/
     *  pitch por técnica) que ya suena para un jugador — pedido explícito del usuario, "que lo
     *  apliquen los enemigos también". */
    private void hit(LivingEntity tgt, double str, double dmgFraction, double knockback) {
        double dmg = PhysicalCombatServer.computeDamage(str, chosen.type(), chosen.damageMult() * dmgFraction);
        tgt.hurt(mob.damageSources().mobAttack(mob), (float) dmg);
        Vec3 away = tgt.position().subtract(mob.position()).normalize();
        tgt.knockback(knockback, -away.x, -away.z);
        PhysicalCombatServer.playImpactSound(mob.level(), tgt, chosen.type());
    }

    /** Empuje propio hacia el objetivo (DASH_PUNCH: la embestida es parte del golpe). */
    private void dashTowards(LivingEntity tgt) {
        Vec3 dir = tgt.position().subtract(mob.position()).normalize();
        mob.setDeltaMovement(mob.getDeltaMovement().add(dir.x * 1.2, 0.1, dir.z * 1.2));
        mob.hurtMarked = true;
    }

    /** Onda en área centrada en `center` (HEAVY_BLOW: remate alrededor del golpe directo;
     *  KIAI: empuje/daño frontal-ish simplificado a un radio, sin cono — un mob no necesita
     *  la precisión direccional que sí le hace falta al jugador). excludeTarget: ver los dos
     *  call sites en fire() para el porqué de cada valor — NO es simétrico entre técnicas.
     *  Mismo sonido que hit() por cada entidad realmente golpeada. */
    private void areaBurst(Vec3 center, double radius, double str, boolean excludeTarget) {
        for (LivingEntity e : mob.level().getEntitiesOfClass(LivingEntity.class,
                new AABB(center, center).inflate(radius),
                x -> x != mob && x.isAlive() && (!excludeTarget || x != target))) {
            double dmg = PhysicalCombatServer.computeDamage(str, chosen.type(), chosen.damageMult() * 0.5);
            e.hurt(mob.damageSources().mobAttack(mob), (float) dmg);
            Vec3 away = e.position().subtract(center).normalize();
            e.setDeltaMovement(e.getDeltaMovement().add(away.x * 0.8, 0.3, away.z * 0.8));
            e.hurtMarked = true;
            PhysicalCombatServer.playImpactSound(mob.level(), e, chosen.type());
        }
    }

    /** STR del mob si tiene stats resueltos; si no, 0 (el daño saldrá mínimo pero no revienta). */
    private static double strengthOf(Mob mob) {
        EntityStats stats = ZenkaiStats.entityStats(mob);
        return stats == null ? 0.0 : stats.computeMeleeFinal();
    }

    /** Entre las técnicas que ya cumplieron cooldown, elige con criterio — ya NO puro azar ni
     *  puro "cualquiera de las listas", sino por SITUACIÓN Y DISTANCIA, pedido explícito:
     *  1) KIAI si el objetivo bloquea o está en pleno wind-up (cargando ki, o mid dash/barrage
     *     del propio jugador) Y ya está a su alcance — "romper la iniciativa" gana a cualquier
     *     otra consideración, con prioridad absoluta.
     *  2) Si no, HEAVY_BLOW/BARRAGE si el objetivo YA está dentro de su alcance real (golpe
     *     directo, no hace falta cerrar distancia); si no, DASH_PUNCH — la embestida solo tiene
     *     sentido a media distancia, no cuando ya está pegado (ver pickByRange).
     *  3) Vida baja del objetivo → reservar el de mayor daño estimado del pool ya filtrado por
     *     situación/distancia como rematador.
     *  4) No repetir el último si hay alternativas en el pool.
     *  Cada paso cae al pool más amplio si su condición no tiene nada listo — nunca deja al mob
     *  sin nada que hacer solo por la situación/distancia si hay algo, sea lo que sea, listo. */
    private EntityPhysicalAttack pickReady(LivingEntity tgt) {
        long now = mob.level().getGameTime();
        List<EntityPhysicalAttack> ready = new java.util.ArrayList<>();
        for (int i = 0; i < attacks.size(); i++) {
            if (now >= readyAt[i]) ready.add(attacks.get(i));
        }
        if (ready.isEmpty()) return null;

        double dist = mob.distanceTo(tgt);

        if (CombatAiUtil.isBlocking(tgt) || CombatAiUtil.isWindingUp(tgt)) {
            EntityPhysicalAttack kiai = byType(ready, PhysicalTechnique.KIAI);
            if (kiai != null && dist <= kiai.range()) return kiai;
        }

        List<EntityPhysicalAttack> pool = pickByRange(ready, dist, closeRangeThreshold);

        if (tgt.getHealth() / tgt.getMaxHealth() <= FINISHER_HEALTH_THRESHOLD) {
            double str = strengthOf(mob);
            EntityPhysicalAttack finisher = pool.stream()
                    .max(java.util.Comparator.comparingDouble(a ->
                            PhysicalCombatServer.computeDamage(str, a.type(), a.damageMult())))
                    .orElse(null);
            if (finisher != null) return finisher;
        }

        if (pool.size() > 1 && lastFired != null) {
            List<EntityPhysicalAttack> notRepeated = pool.stream().filter(a -> a.type() != lastFired).toList();
            if (!notRepeated.isEmpty()) pool = notRepeated;
        }

        return pool.get(mob.getRandom().nextInt(pool.size()));
    }

    /** Reparte por BANDA DE DISTANCIA real, no solo "lo que esté listo": el objetivo YA está
     *  cerca (dist &lt;= closeRangeThreshold) → HEAVY_BLOW/BARRAGE si alguna está lista;
     *  IMPORTANTE — si están en cooldown, NO se fuerza DASH_PUNCH solo porque es lo único listo
     *  (dispararía la embestida con el objetivo ya encima, sin sentido: "el dash lo haga si está
     *  a mediana distancia", pedido explícito) — se cae al pool completo, que ya trae su propio
     *  anti-repetición/azar más abajo. Objetivo lejos (dist &gt; closeRangeThreshold) →
     *  DASH_PUNCH si está lista (la embestida cierra hueco de verdad); si no, pool completo. */
    private static List<EntityPhysicalAttack> pickByRange(List<EntityPhysicalAttack> ready, double dist,
                                                            double closeRangeThreshold) {
        if (dist <= closeRangeThreshold) {
            List<EntityPhysicalAttack> closeReady = ready.stream()
                    .filter(a -> a.type() == PhysicalTechnique.HEAVY_BLOW || a.type() == PhysicalTechnique.BARRAGE)
                    .toList();
            return closeReady.isEmpty() ? ready : closeReady;
        }

        EntityPhysicalAttack dash = byType(ready, PhysicalTechnique.DASH_PUNCH);
        return dash != null ? List.of(dash) : ready;
    }

    private static EntityPhysicalAttack byType(List<EntityPhysicalAttack> pool, PhysicalTechnique type) {
        return pool.stream().filter(a -> a.type() == type).findFirst().orElse(null);
    }

    /** triggerAnim viene de GeoEntity, garantizado por el bound del genérico. */
    private void triggerSafe(String anim) {
        mob.triggerAnim("KiAttack", anim); // ⚠ (controllerName, animName) GeckoLib 4.x
    }
}
