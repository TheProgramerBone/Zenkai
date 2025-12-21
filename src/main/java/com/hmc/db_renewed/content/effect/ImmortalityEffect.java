package com.hmc.db_renewed.content.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;
import com.hmc.db_renewed.core.network.feature.stats.PlayerLifeCycle;
import com.hmc.db_renewed.core.network.feature.stats.PlayerStatsAttachment;


public class ImmortalityEffect extends MobEffect {
    public ImmortalityEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xFF3AD97B);
    }

    @Override
    public boolean applyEffectTick(@NotNull LivingEntity livingEntity, int amplifier) {
        // Solo nos interesa en servidor y para jugadores
        if (!(livingEntity instanceof Player player)) {
            return true;
        }
        if (player.level().isClientSide()) {
            return true;
        }

        PlayerStatsAttachment att = PlayerStatsAttachment.get(player);

        // Si aún no usa el sistema DBR (sin raza elegida), curamos vida vanilla normal
        if (!att.isRaceChosen()) {
            float heal = 2.0F * (amplifier + 1);
            player.heal(heal);
            if (player.getHealth() > player.getMaxHealth()) {
                player.setHealth(player.getMaxHealth());
            }
            return true;
        }

        // ===== Sistema DBR: curar BODY =====
        int bodyMax = att.getBodyMax();
        int bodyCur = att.getBody();

        if (bodyCur <= 0 || bodyMax <= 0) {
            return true;
        }

        // Ejemplo: 2% del body máximo por nivel de amplificador
        // (ajusta la fórmula a tu gusto)
        int regen = (int) Math.max(1,
                Math.round(bodyMax * 0.02 * (amplifier + 1))
        );

        att.addBody(regen); // addBody ya hace clamp a [0, bodyMax]
        int newBody = att.getBody();

        // Sincronizar corazones vanilla para que reflejen el body actual
        float maxHealth = player.getMaxHealth();
        float ratio     = (float) newBody / (float) bodyMax;
        float newHealth = maxHealth * ratio;

        // Evitar números raros
        newHealth = Math.max(0.0F, Math.min(maxHealth, newHealth));
        player.setHealth(newHealth);

        // Sync stats al cliente
        PlayerLifeCycle.syncIfServer(player);

        return true;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        // Si quieres que sea cada 20 ticks (1 s), puedes hacer:
        // return duration % 20 == 0;
        // De momento lo dejamos en "cada tick" como tenías:
        return true;
    }
}
