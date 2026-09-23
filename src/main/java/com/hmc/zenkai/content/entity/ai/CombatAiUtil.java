package com.hmc.zenkai.content.entity.ai;

import com.hmc.zenkai.event.CombatZenkaiHooks;
import com.hmc.zenkai.feature.action.ActionState;
import com.hmc.zenkai.feature.action.ActionStateServer;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Pequeñas consultas situacionales compartidas por {@link KiAttackGoal}/{@link PhysicalAttackGoal}
 * a la hora de elegir QUÉ ataque usar contra un objetivo — nada de esto decide SI atacar (eso lo
 * sigue haciendo cada goal con su propio range/cooldown), solo lee el estado del objetivo.
 */
public final class CombatAiUtil {

    private CombatAiUtil() {}

    /** ¿Está el objetivo en guardia ahora mismo? Reusa el mismo chequeo que ya aplica la
     *  reducción de daño real (jugador vía ActionState/KiCombatServer, mob vía BlockingMob) — ver
     *  CombatZenkaiHooks.isBlockingNow. Un mob atacante lo consulta para preferir un ataque que
     *  tenga más opciones de superar guardia en vez de insistir con el mismo golpe directo. */
    public static boolean isBlocking(LivingEntity target) {
        return CombatZenkaiHooks.isBlockingNow(target);
    }

    /** ¿Está el objetivo en el aire ahora mismo? Para un Player, usa el mismo espejo de vuelo
     *  sincronizado que ya se lee para juzgar el vuelo de OTROS jugadores (ZenkaiCommonAnimations/
     *  AuraTiltController: isFlyEnabled() + !onGround(), server-authoritative). Para cualquier
     *  otro LivingEntity (otro mob) no existe un flag equivalente propio — heurística razonable:
     *  sin gravedad o simplemente no tocando el suelo. */
    public static boolean isAirborne(LivingEntity target) {
        if (target instanceof Player p) {
            return PlayerStatsAttachment.get(p).isFlyEnabled() && !p.onGround();
        }
        return target.isNoGravity() || !target.onGround();
    }

    /** ¿Está ESE atacante en pleno wind-up de un golpe real ahora mismo? Usado por
     *  BlockHabitGoal para bloquear en REACCIÓN a su objetivo actual, no solo por hábito
     *  aleatorio. Dos fuentes según quién sea el atacante:
     *  - Otro mob: {@link AttackTelegraph#isWindingUp()} — puesto por KiAttackGoal/
     *    PhysicalAttackGoal.start()/stop().
     *  - Un jugador: {@link ActionStateServer} — cargando una técnica de ki (CHARGING/
     *    OVERCHARGING, ver ActionState.chargingSlot()) o con una física ACTIVA en curso
     *    (ActionState.physBusy(), p. ej. el dash de DASH_PUNCH/la ráfaga de BARRAGE — las que sí
     *    duran varios ticks reales; las INSTANT como heavy_blow/kiai ya se resolvieron para
     *    cuando esa fase se ve, así que no hay ventana real que bloquear). */
    public static boolean isWindingUp(LivingEntity attacker) {
        if (attacker instanceof ServerPlayer sp) {
            ActionState st = ActionStateServer.get(sp);
            return st.chargingSlot() >= 0 || st.physBusy();
        }
        if (attacker instanceof AttackTelegraph at) return at.isWindingUp();
        return false;
    }
}
