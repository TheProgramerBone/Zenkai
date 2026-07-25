package com.hmc.zenkai.event.tick;

import com.hmc.zenkai.config.CommonConfig;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.feature.skills.SkillEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

/**
 * Movimiento en tierra: coste de estamina del turbo y multiplicador de velocidad.
 *
 * La velocidad YA NO sale de DEX. El techo lo pone la habilidad Run (curva speed_mult del
 * datapack) y punto. Motivo: DEX alimentaba a la vez defensa, velocidad y vuelo, así que
 * cualquier retoque de balance defensivo movía la velocidad de rebote. Ahora DEX es
 * puramente defensivo (ver RaceStatTable) y la velocidad se compra con TP en Run.
 *
 * El TURBO multiplica al final, fuera del escalón de control: así su efecto es visible
 * siempre (+35 %) en lugar de depender del build y del % de poder.
 */
public final class GroundMovementSystem {
    private GroundMovementSystem() {}

    /** Empujón del turbo sobre la velocidad final. Candidato a StatsConfig. */
    private static final double TURBO_SPEED_MULT = 1.35;

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

        // Techo = habilidad Run. Sin niveles de Run, speedMult() vale 1.0 -> velocidad vanilla.
        double maxBonus = Math.min(CommonConfig.speedMultiplierCap(),
                SkillEffects.runSpeedFactor(p)) - 1.0;
        double moveMult = 1.0 + maxBonus * PerformanceTier.of(control) * att.powerFraction();
        if (groundTurbo) moveMult *= TURBO_SPEED_MULT;

        AttributeInstance moveAttr = p.getAttribute(Attributes.MOVEMENT_SPEED);
        if (moveAttr != null) {
            moveAttr.removeModifier(MovementLocks.MOVE_MOD_ID);
            moveAttr.addTransientModifier(new AttributeModifier(
                    MovementLocks.MOVE_MOD_ID, moveMult - 1.0,
                    AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        }
    }
}