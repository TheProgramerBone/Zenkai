package com.hmc.zenkai.feature.action;

/**
 * Fase dentro de una acción. Qué fases son válidas depende del ActionType:
 *   BLOCK         → ACTIVE
 *   PHYSICAL      → ACTIVE (dash, barrage: duran ticks) | INSTANT (heavy blow, kiai)
 *   KI_TECHNIQUE  → CHARGING | RELEASING
 *   TRANSFORM     → STARTING | HOLDING
 */
public enum ActionPhase {
    NONE,
    ACTIVE,
    INSTANT,
    CHARGING,
    RELEASING,
    STARTING,
    HOLDING
}