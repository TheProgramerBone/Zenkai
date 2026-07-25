package com.hmc.zenkai.event.tick;

import com.hmc.zenkai.config.CommonConfig;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.feature.skills.SkillEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

/** Movimiento en tierra: coste de estamina del turbo y multiplicador de velocidad. */
public final class GroundMovementSystem {
    private GroundMovementSystem() {}

    public static void tick(TickCtx c, boolean turboOn) {
        Player p = c.p();
        PlayerStatsAttachment att = c.att();

        boolean control     = p.isSprinting();       // en tierra, Control = esprintar
        boolean groundTurbo = turboOn && control && !p.getAbilities().flying;

        // Solo el turbo cuesta estamina; esprintar normal es gratis.
        if (groundTurbo && p.tickCount % 20 == 0) {
            double drain = CommonConfig.runStaminaDrainPerSecond()
                    * SkillEffects.runStaminaDrainFactor(p);
            if (drain > 0.0) {
                att.addStamina(-(int) Math.max(1, Math.round(drain)));
                if (att.getStamina() <= 0) p.setSprinting(false);
            }
        }

        // El máximo lo marca DEX (speedStat, con su tope) y lo eleva la habilidad Run.
        double speedStat = att.computeSpeedFinal();
        double max = Math.min(1.0 + (speedStat / 100.0) * CommonConfig.movementScaling(),
                CommonConfig.speedMultiplierCap()) * SkillEffects.runSpeedFactor(p);
        double moveMult = 1.0 + (max - 1.0)
                * PerformanceTier.of(control, turboOn) * att.powerFraction();

        AttributeInstance moveAttr = p.getAttribute(Attributes.MOVEMENT_SPEED);
        if (moveAttr != null) {
            moveAttr.removeModifier(MovementLocks.MOVE_MOD_ID);
            moveAttr.addTransientModifier(new AttributeModifier(
                    MovementLocks.MOVE_MOD_ID, moveMult - 1.0,
                    AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        }
    }
}