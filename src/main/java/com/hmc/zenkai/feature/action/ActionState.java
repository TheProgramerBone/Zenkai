package com.hmc.zenkai.feature.action;

/**
 * Qué está haciendo un jugador AHORA MISMO. Una acción exclusiva a la vez.
 *
 * REPARTO DE RESPONSABILIDAD (importante para el paso 5):
 *   ActionState                  → identidad, fase y tiempo de la acción
 *   KiChargeServer               → sonido y difusión de la bola de carga
 *   PhysicalCombatServer.ACTIVE  → dirección, ids ya golpeados, ticks restantes
 *   KiCombatServer               → cooldowns, barreras, modificador de velocidad
 *   CombatModeServerState, flags → estados sostenidos
 *
 * No se solapan: ActionState nunca guarda mecánica y los almacenes nunca deciden identidad.
 *
 * payload:
 *   KI_TECHNIQUE → slot de la técnica (0..N)
 *   PHYSICAL     → ordinal de PhysicalTechnique
 *   resto        → -1
 *
 * El progreso NO se guarda: se deriva de startTick. Guardar chargeTicks sería un valor
 * duplicado y susceptible de desincronizarse, que es justo el bug que cerramos en el paso 2.
 */
public record ActionState(ActionType type, ActionPhase phase, long startTick, int payload) {

    public static final ActionState NONE =
            new ActionState(ActionType.NONE, ActionPhase.NONE, 0L, -1);

    public boolean isNone() { return type == ActionType.NONE; }

    public long elapsed(long now) {
        return isNone() ? 0L : Math.max(0L, now - startTick);
    }

    /** Slot de técnica de ki en curso, o -1. */
    public int chargingSlot() {
        return (type == ActionType.KI_TECHNIQUE && phase == ActionPhase.CHARGING) ? payload : -1;
    }

    /** ¿Movimiento físico CON DURACIÓN en curso? (los instantáneos no dejan estado). */
    public boolean physBusy() {
        return type == ActionType.PHYSICAL && phase == ActionPhase.ACTIVE;
    }
}