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
        // Fracción de la curación de body que es vida ganada de verdad (1 = toda).
        double bodyNetFraction = 1.0;

        int bodyCur = att.getBody(), bodyMax = att.getBodyMax();
        if (bodyCur > 0 && bodyCur < bodyMax) {
            double baseRegen = bodyMax * (ServerConfig.baseRegenBody() / 100.0)
                    * RacePassives.bodyRegenMult(p)
                    * CombatRegen.bodyMult(p);
            // Forma divina del namekiano (namek_god): regeneración extra casi gratis en ki.
            double legendRegen = bodyMax * RacePassives.namekLegendRegenFraction(p);
            // «Ancestros» (forma Legendaria del namekiano): sin regeneración natural; solo cura peleando (CombatZenkaiHooks).
            if (ServerConfig.namekLegendBlockRegen() && RacePassives.wearingNamekLegendary(p)) baseRegen = 0.0;
            double regenPerSecond = baseRegen + legendRegen;
            bodyNetFraction = kaiokenNetFraction(c, regenPerSecond);
            int gain = accrue(carry, 0, regenPerSecond);
            if (gain > 0) {
                att.addBody(gain);
                didBody = true;
                // El coste se reparte según de dónde viene cada punto: la pasiva de siempre y la de la forma.
                double legendShare = regenPerSecond > 0.0 ? legendRegen / regenPerSecond : 0.0;
                int legendGain = (int) Math.round(gain * legendShare);
                RacePassiveSystem.chargeNamekianRegen(p, att, gain - legendGain);
                if (legendGain > 0) {
                    att.consumeEnergy((int) Math.ceil(legendGain * ServerConfig.namekLegendRegenKiPerPoint()));
                }
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
            if (didBody)    food.addExhaustion((float) (ServerConfig.regenBodyExhaustion() * bodyNetFraction));
            if (didStamina) food.addExhaustion((float) ServerConfig.regenStaminaExhaustion());
        }
    }

    /**
     * Con el Kaioken activo, qué parte de la regen de body es curación NETA: (regen - quema) /
     * regen, en [0, 1]. Sin Kaioken, 1. La comida paga solo esa parte (2026-10-12): antes el
     * cobro era fijo por segundo y, como el Kaioken te tiene siempre por debajo del máximo, la
     * regen corría sin parar y se comía un muslo cada ~7 s en CUALQUIER escalón, pagando vida que
     * el propio Kaioken se volvía a llevar. Así, un escalón que te va bajando la vida no cuesta
     * comida (ya lo limitan la vida y el strain) y uno que tu CON sostiene paga lo que cura de
     * más, de modo que mantener x2 para siempre sigue sin ser gratis.
     */
    private static double kaiokenNetFraction(TickCtx c, double regenPerSecond) {
        if (regenPerSecond <= 0.0 || !c.form().getKaioken().isOn()) return 1.0;
        double burnPerSecond = KaiokenSystem.drainPerTick(c.att(), c.form(),
                SkillEffects.kaiokenDrainFactor(c.p())) * 20.0;
        return Math.max(0.0, Math.min(1.0, (regenPerSecond - burnPerSecond) / regenPerSecond));
    }

    /** Acumula la fracción y devuelve los puntos enteros a otorgar este segundo. */
    private static int accrue(double[] carry, int idx, double amount) {
        carry[idx] += amount;
        int whole = (int) carry[idx];
        if (whole > 0) carry[idx] -= whole;
        return whole;
    }
}