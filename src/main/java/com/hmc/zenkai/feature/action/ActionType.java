package com.hmc.zenkai.feature.action;

/**
 * Acciones EXCLUSIVAS: solo una puede estar activa a la vez.
 * Deliberadamente NO están aquí los estados sostenidos, que son independientes y pueden
 * coexistir entre sí y con cualquier acción:
 *   · chargingKi (tecla C) — acumula el recurso de ki, sin slot ni técnica
 *   · turbo                — drena ki, +velocidad, intensifica el aura
 *   · flying · combatMode · downed
 * Meter chargingKi aquí fue el error del primer borrador: comparte la palabra "carga" con
 * KI_TECHNIQUE/CHARGING y no comparte absolutamente nada más.
 */
public enum ActionType {
    NONE,
    BLOCK,
    PHYSICAL,
    KI_TECHNIQUE,
    TRANSFORM
}