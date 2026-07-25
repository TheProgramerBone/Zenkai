package com.hmc.zenkai.event.tick;

import com.hmc.zenkai.config.CommonConfig;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.feature.skills.SkillEffects;
import net.minecraft.world.entity.player.Player;

/** Regeneración pasiva de body / stamina / ki, una vez por segundo. */
public final class RegenSystem {
    private RegenSystem() {}

    public static void tick(TickCtx c) {
        Player p = c.p();
        PlayerStatsAttachment att = c.att();
        if (p.tickCount % 20 != 0) return;

        var food = p.getFoodData();
        if (!p.isCreative() && food.getFoodLevel() <= 0) return;

        // carry[0] lo comparte KaiokenSystem: si el kaioken está activo, lo que sumamos aquí
        // se resta de su quema (mecánica intencional, ver PlayerTickState#carry).
        double[] carry = PlayerTickState.carry(p.getUUID());
        boolean didBody = false, didStamina = false;

        int bodyCur = att.getBody(), bodyMax = att.getBodyMax();
        if (bodyCur > 0 && bodyCur < bodyMax) {
            int gain = accrue(carry, 0, bodyMax * (CommonConfig.baseRegenBody() / 100.0));
            if (gain > 0) { att.addBody(gain); didBody = true; }
        }

        // Correr en turbo drena estamina: no se regenera a la vez o se anularían entre sí.
        int stCur = att.getStamina(), stMax = att.getStaminaMax();
        if (stCur < stMax && !p.isSprinting()) {
            int gain = accrue(carry, 1, stMax * (CommonConfig.baseRegenStamina() / 100.0));
            if (gain > 0) { att.addStamina(gain); didStamina = true; }
        }

        // El regen pasivo de ki existe siempre (mínimo vital); Meditación lo multiplica.
        int kiCur = att.getEnergy(), kiMax = att.getEnergyMax();
        if (kiCur < kiMax) {
            int gain = accrue(carry, 2, kiMax * (CommonConfig.baseRegenEnergy() / 100.0)
                    * SkillEffects.kiRegenFactor(p));
            if (gain > 0) att.addEnergy(gain);
        }

        if (!p.isCreative()) {
            if (didBody)    food.addExhaustion(2.4F);
            if (didStamina) food.addExhaustion(0.6F);
        }
    }

    /** Acumula la fracción y devuelve los puntos enteros a otorgar este segundo. */
    private static int accrue(double[] carry, int idx, double amount) {
        carry[idx] += amount;
        int whole = (int) carry[idx];
        if (whole > 0) carry[idx] -= whole;
        return whole;
    }
}