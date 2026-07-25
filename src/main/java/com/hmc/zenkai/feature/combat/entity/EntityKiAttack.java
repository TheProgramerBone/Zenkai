package com.hmc.zenkai.feature.combat.entity;

import com.hmc.zenkai.feature.technique.KiTechniqueType;

/**
 * Un ataque de ki que una entidad puede lanzar, tal cual viene del datapack. Referencia un
 * KiTechniqueType existente (wave, big_blast...), así que el mob dispara EXACTAMENTE las mismas
 * técnicas que los jugadores, con su estela, velocidad y comportamiento defensivo.
 *
 * El daño NO está aquí: sale de KiCombatServer.computeDamage con el WIL de la entidad (sus
 * stats del JSON), por damageMult. Un mob más fuerte pega más sin tocar el ataque.
 *
 * cooldown/range van POR entrada: dos ataques distintos tienen su propio ritmo y alcance.
 */
public record EntityKiAttack(KiTechniqueType type, int size, int rgb,
                             int cooldownTicks, double range, double damageMult) {

    /** Vacío si el tipo no existe (JSON con un nombre mal escrito): la entrada se descarta. */
    public boolean valid() { return type != null; }
}