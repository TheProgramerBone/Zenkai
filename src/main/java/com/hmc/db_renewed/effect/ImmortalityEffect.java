package com.hmc.db_renewed.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;

public class ImmortalityEffect extends MobEffect {
    public ImmortalityEffect() {
        super(MobEffectCategory.NEUTRAL, 0xFF3AD97B);
    }

    @Override
    public boolean applyEffectTick(@NotNull LivingEntity livingEntity, int amplifier) {
        if (livingEntity instanceof Player) {
            // Regeneración ajustable según el amplificador
            livingEntity.heal(2.0F * (amplifier + 1));

            // Limita la salud al máximo para evitar que exceda el valor
            if (livingEntity.getHealth() > livingEntity.getMaxHealth()) {
                livingEntity.setHealth(livingEntity.getMaxHealth());
            }
        }
        return true;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        // Se podría agregar una condición aquí para hacerlo menos frecuente, si deseas
        return true;
    }
}
