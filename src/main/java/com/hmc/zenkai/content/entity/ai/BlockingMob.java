package com.hmc.zenkai.content.entity.ai;

/**
 * Implementado por mobs que saben bloquear (hoy solo {@link
 * com.hmc.zenkai.content.entity.misc.ShadowCloneEntity}, vía {@link BlockHabitGoal}). Generaliza
 * lo que antes solo existía para jugadores (KiCombatServer.isBlocking/SkillEffects.
 * blockDamageMultiplier, atados a ActionState y a la skill ki_block) — ver los usos en
 * CombatZenkaiHooks.isBlockingNow/blockDamageMultiplierOf y PhysicalCombatServer.impactFx.
 *
 * Un mob no tiene la skill ki_block (no tiene skills), así que blockDamageMultiplier() es un
 * número fijo por entidad en vez de una curva por nivel.
 */
public interface BlockingMob {
    boolean isBlockingNow();

    void setBlocking(boolean blocking);

    /** Multiplicador de daño mientras bloquea (1.0 = sin reducción, menos es mejor). */
    double blockDamageMultiplier();
}
