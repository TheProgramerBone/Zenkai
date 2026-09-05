package com.hmc.zenkai.event.tick;

import com.hmc.zenkai.config.ServerConfig;
import com.hmc.zenkai.feature.combat.CombatRegen;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.feature.race.RacePassives;
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

        // "Muerto" (atrapado en el otro mundo, ver OtherworldManager.isInOtherworld): el hambre
        // no tiene sentido ahí — comida y saturación al máximo cada segundo, y exhaustion a 0
        // para que no se acumule entre correcciones y se coma un punto de golpe justo antes del
        // siguiente refresco. No corta el resto del regen de abajo: solo el hambre deja de
        // importar, body/stamina/ki siguen funcionando con normalidad.
        if (att.isInOtherworld()) {
            food.setFoodLevel(20);
            food.setSaturation(20f);
            food.setExhaustion(0f);
        }

        // Suelo de comida: antes se regeneraba hasta dejarte en 1 de hambre, así que era
        // imposible acumular reserva. Vanilla corta su regen por debajo de 18; aquí se corta
        // mucho más abajo para poder curarse con hambre, pero no hasta vaciarte.
        if (!p.isCreative() && food.getFoodLevel() <= ServerConfig.regenMinFoodLevel()) return;

        // carry[0] lo comparte KaiokenSystem: si el kaioken está activo, lo que sumamos aquí
        // se resta de su quema (mecánica intencional, ver PlayerTickState#carry).
        double[] carry = PlayerTickState.carry(p.getUUID());
        boolean didBody = false, didStamina = false;

        int bodyCur = att.getBody(), bodyMax = att.getBodyMax();
        if (bodyCur > 0 && bodyCur < bodyMax) {
            int gain = accrue(carry, 0, bodyMax * (ServerConfig.baseRegenBody() / 100.0)
                    * RacePassives.bodyRegenMult(p)
                    * CombatRegen.bodyMult(p));
            if (gain > 0) {
                att.addBody(gain);
                didBody = true;
                RacePassiveSystem.chargeNamekianRegen(p, att, gain);
            }
        }

        int stCur = att.getStamina(), stMax = att.getStaminaMax();
        if (stCur < stMax && !p.isSprinting()) {
            int gain = accrue(carry, 1, stMax * (ServerConfig.baseRegenStamina() / 100.0)
                    * RacePassives.staminaRegenMult(p));
            if (gain > 0) { att.addStamina(gain); didStamina = true; }
        }

        // El regen pasivo de ki existe siempre (mínimo vital); Meditación lo multiplica.
        int kiCur = att.getEnergy(), kiMax = att.getEnergyMax();
        if (kiCur < kiMax) {
            int gain = accrue(carry, 2, kiMax * (ServerConfig.baseRegenEnergy() / 100.0)
                    * SkillEffects.kiRegenFactor(p));
            if (gain > 0) att.addEnergy(gain);
        }

        if (!p.isCreative()) {
            if (didBody)    food.addExhaustion((float) ServerConfig.regenBodyExhaustion());
            if (didStamina) food.addExhaustion((float) ServerConfig.regenStaminaExhaustion());
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