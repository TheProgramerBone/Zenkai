package com.hmc.zenkai.content.entity.ai;

/**
 * Implementado por {@link com.hmc.zenkai.content.entity.ZenkaiDefaultMob} (así que CUALQUIER
 * zenkaimob con KiAttackGoal/PhysicalAttackGoal lo trae gratis, no solo la Sombra): expone si el
 * mob está en pleno wind-up de un ataque real ahora mismo, sin necesidad de sincronizar nada al
 * cliente — la decisión de "¿debo bloquear porque mi objetivo está a punto de golpear?" (ver
 * BlockHabitGoal) es puramente server-side, un mob solo mira el flag del OTRO mob que lo tiene
 * como objetivo.
 *
 * Distinto de {@link PosedAttacker} (que es un concern de RENDER, solo lo implementan mobs sin
 * GeckoLib de verdad como la Sombra): AttackTelegraph es un concern de IA/decisión, universal.
 * {@code KiAttackGoal}/{@code PhysicalAttackGoal} ponen este flag a true en start() y a false en
 * stop(), igual que ya hacían con PosedAttacker#setAttackPose pero para un propósito distinto.
 */
public interface AttackTelegraph {
    void setWindingUp(boolean windingUp);

    boolean isWindingUp();
}
