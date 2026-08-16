package com.hmc.zenkai.feature.action;

/**
 * Qué está haciendo un jugador AHORA MISMO. Una acción exclusiva a la vez.
 * REPARTO DE RESPONSABILIDAD (importante para el paso 5):
 *   ActionState                  → identidad, fase y tiempo de la acción
 *   KiChargeServer               → sonido y difusión de la bola de carga
 *   PhysicalCombatServer.ACTIVE  → dirección, ids ya golpeados, ticks restantes
 *   KiCombatServer               → cooldowns, barreras, modificador de velocidad
 *   CombatModeServerState, flags → estados sostenidos
 * No se solapan: ActionState nunca guarda mecánica y los almacenes nunca deciden identidad.
 * payload:
 *   KI_TECHNIQUE → slot de la técnica (0..N)
 *   PHYSICAL     → ordinal de PhysicalTechnique
 *   resto        → -1
 * El progreso NO se guarda: se deriva de startTick. Guardar chargeTicks sería un valor
 * duplicado y susceptible de desincronizarse, que es justo el bug que cerramos en el paso 2.
 /**
 * ...
 * visual: canal de PRESENTACIÓN, separado del payload a propósito. Para KI_TECHNIQUE lleva
 * el animSet elegido en el editor. Existe porque las KiTechnique de un jugador solo se
 * sincronizan consigo mismo: sin esto, un observador sabe que el vecino carga el slot 2 pero
 * no qué animación le corresponde. Sincronizar las ocho técnicas de cada jugador visible
 * para eso era desproporcionado.
 * No influye en gameplay. Nunca leerlo para decidir nada.
 */
public record ActionState(ActionType type, ActionPhase phase, long startTick,
                          int payload, int visual) {

    public static final ActionState NONE =
            new ActionState(ActionType.NONE, ActionPhase.NONE, 0L, -1, 0);

    public boolean isNone() { return type == ActionType.NONE; }

    public long elapsed(long now) {
        return isNone() ? 0L : Math.max(0L, now - startTick);
    }

    public int chargingSlot() {
        return (type == ActionType.KI_TECHNIQUE
                && (phase == ActionPhase.CHARGING || phase == ActionPhase.OVERCHARGING))
                ? payload : -1;
    }

    public boolean physBusy() {
        return type == ActionType.PHYSICAL && phase == ActionPhase.ACTIVE;
    }

    public ActionState withPhase(ActionPhase p) {
        return new ActionState(type, p, startTick, payload, visual);
    }
}