package com.hmc.zenkai.feature.alignment;

import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.player.Player;

/**
 * Fábrica única de la IA de miedo por alineamiento: tanto {@code NamekianEntity} (goal propia)
 * como {@code VillagerFearHook} (Villager vainilla, vía evento) usan EXACTAMENTE la misma
 * condición y los mismos números. Sin esta fábrica compartida, un ajuste de distancia/velocidad
 * o del umbral EVIL divergiría entre las dos razas sin que nadie se diera cuenta.
 *
 * Pura evasión de movimiento: NO toca precios de trade ni ningún otro comportamiento — el
 * "miedo" pedido es que huyan de verdad, no una penalización económica.
 */
public final class AlignmentFearGoals {
    private AlignmentFearGoals() {}

    private static final float  MAX_DIST     = 10.0F;
    private static final double WALK_SPEED   = 1.0D;
    private static final double SPRINT_SPEED = 1.4D;

    public static AvoidEntityGoal<Player> avoidingEvilPlayers(PathfinderMob mob) {
        // avoidPredicate filtra QUÉ jugadores cuentan como amenaza (los EVIL); el último
        // predicado (predicateOnAvoidEntity) es el mismo filtro creativo/espectador que usa
        // por defecto el overload corto de vainilla (EntitySelector.NO_CREATIVE_OR_SPECTATOR).
        return new AvoidEntityGoal<>(mob, Player.class, AlignmentFearGoals::isEvilPlayer,
                MAX_DIST, WALK_SPEED, SPRINT_SPEED, EntitySelector.NO_CREATIVE_OR_SPECTATOR::test);
    }

    private static boolean isEvilPlayer(LivingEntity le) {
        return le instanceof Player p
                && AlignmentTier.of(PlayerStatsAttachment.get(p).getAlignment()) == AlignmentTier.EVIL;
    }
}
