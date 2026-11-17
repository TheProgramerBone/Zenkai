package com.hmc.zenkai.feature.action;

import com.hmc.zenkai.feature.combat.CombatModeServerState;
import com.hmc.zenkai.feature.combat.GuardHands;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.feature.technique.KiChargeServer;
import com.hmc.zenkai.feature.technique.KiCombatServer;
import com.hmc.zenkai.feature.technique.PhysicalCombatServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Construye el ActionContext desde los almacenes de servidor. Único sitio donde se sabe de
 * dónde sale cada booleano; a partir de aquí las reglas son las mismas que usa el cliente.
 * Cuando el paso 5 retire los almacenes paralelos (KiChargeServer.ACTIVE, BLOCKING,
 * PhysicalCombatServer.ACTIVE, CombatModeServerState), este es el ÚNICO archivo que cambia.
 * Identidad y tiempo salen de ActionState; los estados sostenidos, de sus almacenes.
 * Los almacenes que quedan NO son paralelos y no se retiran:
 *   CombatModeServerState  → estado sostenido (toggle de modo combate)
 *   PhysicalCombatServer   → mecánica del movimiento (dirección, ids golpeados, ticks)
 *   KiCombatServer         → cooldowns, barreras, modificador de velocidad
 *   flags                  → chargingKi, turbo, downed, vuelo
 * KiCombatServer.isBlocking() ya consulta ActionState, así que este method no lee dos
 * fuentes distintas para lo mismo.
 */
public final class ServerActionContext {

    private ServerActionContext() {}

    public static ActionContext of(ServerPlayer sp) {
        PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);

        // Identidad y tiempo salen de ActionState; los estados sostenidos, de sus almacenes.
        // Cuando el paso 3 invierta la dependencia, esta clase no cambia.
        ActionState st = ActionStateServer.get(sp);
        long now = sp.level().getGameTime();

        return new ActionContext(
                att.isRaceChosen(),
                att.isCombatActive() && CombatModeServerState.isActive(sp.getUUID()),
                KiCombatServer.isBlocking(sp),
                att.flags().isDowned(),
                GuardHands.handsFree(sp),
                GuardHands.kiHandsFree(sp),
                GuardHands.isHoldingSword(sp),
                st.physBusy(),
                st.chargingSlot(),
                st.elapsed(now),
                st.inClash(),
                com.hmc.zenkai.feature.combat.StunState.isStunned(sp),
                handsBusy(sp));
    }

    /**
     * «Manos ocupadas»: lleva un cuerpo en brazos o está dentro de una cámara de contención de ki.
     * Las dos situaciones bloquean lo mismo (pelear, bloquear, lanzar técnicas), así que comparten
     * el booleano carryingBody del contexto en vez de abrir otro campo con reglas idénticas.
     */
    private static boolean handsBusy(ServerPlayer sp) {
        return com.hmc.zenkai.feature.player.death.PlayerBodyCarry.restricted(sp)
                || com.hmc.zenkai.feature.kichamber.KiChamberSessions.isInside(sp);
    }

    /** Contexto con un ActionState dado en vez del actual. Lo usa el release de ki, que ya
     *  apagó la carga cuando construye el contexto: con of() el chargingSlot sería -1 y
     *  disparo daría NO_CHARGE. */
    public static ActionContext snapshot(ServerPlayer sp, ActionState st, long now) {
        PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
        return new ActionContext(
                att.isRaceChosen(),
                att.isCombatActive() && CombatModeServerState.isActive(sp.getUUID()),
                KiCombatServer.isBlocking(sp),
                att.flags().isDowned(),
                GuardHands.handsFree(sp),
                GuardHands.kiHandsFree(sp),
                GuardHands.isHoldingSword(sp),
                st.physBusy(),
                st.chargingSlot(),
                st.elapsed(now),
                st.inClash(),
                com.hmc.zenkai.feature.combat.StunState.isStunned(sp),
                handsBusy(sp));
    }
}