package com.hmc.zenkai.content.entity.ai;

import com.hmc.zenkai.content.entity.ZenkaiDefaultMob;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@code MeleeAttackGoal} vainilla, pero respetando el mismo respiro compartido
 * ({@link ZenkaiDefaultMob#isActionOnCooldown()}) que ya frena a KiAttackGoal/PhysicalAttackGoal
 * entre sí. SIN esto, en cuanto un ataque especial terminaba y entraba en su respiro, el melee
 * vainilla (que no sabe nada de ese respiro) rellenaba el hueco INMEDIATAMENTE — la pausa dejaba
 * de notarse en la práctica, justo lo reportado por el usuario ("no hay cooldown observable entre
 * habilidades físicas"): técnicamente sí había un respiro entre dos técnicas, pero un golpe de
 * melee normal lo tapaba por completo. Solo gatea canUse() (empezar uno nuevo) — un melee YA en
 * marcha no se corta a mitad si el respiro se activa mientras corre.
 *
 * isInterruptable(): protege el swing ("strike", ~7 ticks) de ser cortado por Ki/Físicas
 * (mayor prioridad) a mitad de reproducirse — la misma protección que KiAttackGoal/
 * PhysicalAttackGoal ya se dan entre sí vía su propio windup, que a Melee (la de menor
 * prioridad de las tres, así que nunca podía desalojar a las otras dos, pero SÍ podía ser
 * desalojada) nunca le llegaba. Auditado explícitamente a pedido del usuario: sin esto, un Ki/
 * Físicas cuyo canUse() se vuelve true justo en esos ~7 ticks (p. ej. el cooldown de una técnica
 * acaba de cumplirse) desaloja a Melee y dispara su propio trigger en el MISMO controlador
 * GeckoLib ("KiAttack"), cortando "strike" de golpe — confirmado leyendo el código fuente de
 * GeckoLib (AnimationController.setAnimation/tryTriggerAnimation): un trigger nuevo siempre
 * reemplaza/hace stop() de lo que estuviera sonando en ese controlador, sin excepción. Ver
 * ZenkaiDefaultMob.isMeleeStrikePlaying().
 */
public class ZenkaiMeleeAttackGoal extends MeleeAttackGoal {

    private static final Logger LOGGER = LoggerFactory.getLogger("Zenkai-CombatAI");
    private static final int DIAG_LOG_INTERVAL_TICKS = 100; // ~5s, ver el mismo campo en KiAttackGoal

    private final ZenkaiDefaultMob zenkaiMob;
    private long lastDiagLog = Long.MIN_VALUE;

    public ZenkaiMeleeAttackGoal(ZenkaiDefaultMob mob, double speedModifier, boolean followingTargetEvenIfNotSeen) {
        super(mob, speedModifier, followingTargetEvenIfNotSeen);
        this.zenkaiMob = mob;
    }

    /** Diagnóstico temporal (quitar cuando se confirme en juego, mismo criterio que KiAttackGoal/
     *  PhysicalAttackGoal): esta clase no tenía NINGÚN log — para saber si el problema real de
     *  "no persigue en tierra" es que nunca llega a intentarlo (respiro/sin target) o que SÍ lo
     *  intenta pero super.canUse() (createPath de MeleeAttackGoal vainilla) falla, hace falta ver
     *  qué PathNavigation tiene instalada de verdad en ese momento. */
    @Override
    public boolean canUse() {
        if (zenkaiMob.isActionOnCooldown()) {
            logDiag("en respiro (isActionOnCooldown)");
            return false;
        }
        boolean can = super.canUse();
        if (!can) logDiag("super.canUse() false (sin target, o MeleeAttackGoal.createPath no encontró camino)");
        return can;
    }

    private void logDiag(String reason) {
        long now = zenkaiMob.level().getGameTime();
        if (now - lastDiagLog < DIAG_LOG_INTERVAL_TICKS) return;
        lastDiagLog = now;
        LOGGER.info("[Zenkai] {} (melee) no ataca ({}): target={} nav={} moveControl={}",
                zenkaiMob, reason, zenkaiMob.getTarget(),
                zenkaiMob.getNavigation().getClass().getSimpleName(),
                zenkaiMob.getMoveControl().getClass().getSimpleName());
    }

    @Override
    public boolean isInterruptable() {
        return !zenkaiMob.isMeleeStrikePlaying();
    }
}
