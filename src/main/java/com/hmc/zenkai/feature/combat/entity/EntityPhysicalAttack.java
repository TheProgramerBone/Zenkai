package com.hmc.zenkai.feature.combat.entity;

import com.hmc.zenkai.feature.technique.PhysicalTechnique;

/**
 * Una técnica física que una entidad puede usar, tal cual viene del datapack. Espejo de
 * {@link EntityKiAttack} pero sin proyectil: el efecto (daño + empuje/aturdimiento) se aplica
 * directamente sobre el objetivo — ver {@link com.hmc.zenkai.content.entity.ai.PhysicalAttackGoal}.
 *
 * El daño NO está aquí: sale de PhysicalCombatServer.computeDamage con la fuerza (STR) de la
 * entidad (sus stats del JSON), por damageMult. Un mob más fuerte pega más sin tocar el ataque.
 *
 * cooldown/range van POR entrada: dos técnicas distintas tienen su propio ritmo y alcance.
 */
public record EntityPhysicalAttack(PhysicalTechnique type, int cooldownTicks, double range,
                                    double damageMult) {

    /** Vacío si el tipo no existe (JSON con un nombre mal escrito): la entrada se descarta. */
    public boolean valid() { return type != null; }
}
