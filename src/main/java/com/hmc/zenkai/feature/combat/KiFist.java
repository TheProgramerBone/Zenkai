package com.hmc.zenkai.feature.combat;

import com.hmc.zenkai.config.CommonConfig;
import com.hmc.zenkai.feature.skills.SkillEffects;
import com.hmc.zenkai.feature.skills.SkillToggles;
import net.minecraft.world.entity.player.Player;

/**
 * Ki Fist: envolver los puños en ki. Hace DOS cosas a la vez, y conviene no confundirlas:
 *
 *  1. SUMA daño en función de SPI. La fuente del golpe sigue siendo STR — esto es un extra,
 *     no un reemplazo. El SPI se valora en la escala del MELEE (SPI x coeficiente de melee de
 *     la raza/estilo) y no en la del pool de ki: el coeficiente de reservas va de 40 a 120 por
 *     punto porque describe una BARRA, mientras el de melee va de 3.4 a 11. Multiplicar por el
 *     de reservas habría metido el bonus en una escala diez veces mayor que el daño y habría
 *     hecho falta una curva de 0.02 para compensar, con la identidad de raza descuadrada.
 *
 *  2. CAMBIA EL RECURSO: el golpe se paga en ki en vez de en estamina. Esa es la razón de ser
 *     de la habilidad para un ki user, que tiene SPI de sobra y poco CON. Un warrior también
 *     la aprovecha, porque igualmente necesita SPI para sostener sus transformaciones.
 *
 * SIN KI NO SE ROMPE NADA: si no llega para pagar, el golpe sale como un melee normal
 * cobrando estamina y sin bonus. Nunca deja al jugador sin poder pegar.
 */
public final class KiFist {
    private KiFist() {}

    /**
     * Resultado de intentar pagar un golpe con Ki Fist.
     * @param paidWithKi true si el ki cubrió el golpe: quien llama NO debe cobrar estamina.
     * @param bonus      daño extra a sumar (0 si no se pagó).
     */
    public record Result(boolean paidWithKi, double bonus) {
        public static final Result NONE = new Result(false, 0.0);
    }

    public static boolean isOn(Player p) {
        return SkillToggles.isOn(p, SkillEffects.KI_FIST);
    }

    /** Daño extra bruto, sin escalar por la carga del golpe. 0 si el interruptor está apagado. */
    public static double rawBonus(Player p, ZenkaiCombatStats st) {
        if (!isOn(p)) return 0.0;
        double f = SkillEffects.kiFistFactor(p);
        return f <= 0.0 ? 0.0 : st.computeSpiritMeleeFinal() * f;
    }

    /**
     * Coste en ki de un golpe con Ki Fist. FÓRMULA ÚNICA: la usan el melee básico, las
     * técnicas físicas y la predicción del cliente.
     *   coste = (STR x ki_por_golpe  +  bonus x ki_por_daño_extra) x multMovimiento x multRaza
     * Las dos mitades por separado a propósito: la primera es lo que antes costaba en
     * estamina y la segunda es lo que la habilidad AÑADE, así que se pueden balancear sin
     * tocarse (cost.melee_ki_per_hit y cost.ki_per_bonus_damage).
     *
     * @param strRef    daño STR de referencia del golpe
     * @param bonus     bonus ya aplicado a ese golpe
     * @param costMult  multiplicador del movimiento (1.0 = golpe básico)
     */
    public static int kiCost(ZenkaiCombatStats st, double strRef, double bonus, double costMult) {
        double raw = (strRef * CommonConfig.meleeKiPerHit()
                + bonus * CommonConfig.kiPerBonusDamage()) * costMult * st.kiCostMult();
        return (int) Math.max(1, Math.ceil(raw));
    }

    /**
     * Golpe básico: intenta pagarlo en ki.
     * @param strDamage daño STR SIN escalar por la carga
     * @param chargeF   escala de carga del golpe (0.2 a 1.0)
     * @return NONE si el interruptor está apagado o no hay ki: quien llama cobra estamina.
     */
    public static Result spendForMelee(Player p, ZenkaiCombatStats st,
                                       double strDamage, double chargeF) {
        if (!isOn(p)) return Result.NONE;

        double bonus = rawBonus(p, st) * chargeF;
        int cost = kiCost(st, strDamage * chargeF, bonus, 1.0);
        if (st.getEnergy() < cost) return Result.NONE;   // caída silenciosa a estamina

        st.consumeEnergy(cost);
        return new Result(true, bonus);
    }
}