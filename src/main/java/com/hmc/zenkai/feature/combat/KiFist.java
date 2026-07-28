package com.hmc.zenkai.feature.combat;

import com.hmc.zenkai.feature.skills.SkillEffects;
import com.hmc.zenkai.feature.skills.SkillToggles;
import net.minecraft.world.entity.player.Player;

/**
 * Ki Fist: envolver los puños en ki para pegar más fuerte.
 *
 * EL RECURSO NO CAMBIA. La estamina se sigue cobrando por el STR igual que siempre, y el ki
 * se cobra APARTE por el bonus. Golpear cansa, tengas ki o no; el ki es lo que pagas por el
 * extra. Llevar Ki Fist y Ki Infuse a la vez cuesta estamina una vez y ki dos veces, una por
 * cada bonus.
 *
 * El bonus sale de SPI valorado en la escala del MELEE (SPI x coeficiente de melee de la
 * raza/estilo), no en la del pool de ki: el coeficiente de reservas va de 40 a 120 por punto
 * porque describe una BARRA, mientras el de melee va de 3.4 a 11. Multiplicar por el de
 * reservas habría metido el bonus en una escala diez veces mayor que el daño, obligando a una
 * curva de 0.02 para compensar y descuadrando la identidad de cada raza.
 *
 * La fuente del golpe sigue siendo STR: esto SUMA, no sustituye. Un ki user con poco STR
 * pasa de no poder pelear de cerca a poder defenderse, sin acercarse al daño de un físico.
 *
 * Misma forma exacta que KiInfusion (bonus + coste sobre el bonus) y misma fórmula de coste,
 * que vive en KiInfusion.kiCost: un solo sitio donde se decide cuánto ki vale un punto de
 * daño extra, para los puños, las armas y lo que venga después.
 */
public final class KiFist {
    private KiFist() {}

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
     * Golpe básico: calcula el bonus, COBRA el ki y devuelve el bonus ya escalado por la
     * carga. CAÍDA SILENCIOSA: sin ki suficiente devuelve 0 y no cobra nada — el golpe sale
     * como un melee normal, que ya se paga con su estamina de siempre.
     * Cobrar aquí dentro y no en quien llama es a propósito: coste y daño salen del mismo
     * número y no pueden descuadrarse si alguien toca uno de los dos.
     */
    public static double spendForMelee(Player p, ZenkaiCombatStats st, double chargeF) {
        double bonus = rawBonus(p, st) * chargeF;
        if (bonus <= 0.0) return 0.0;

        int cost = KiInfusion.kiCost(st, bonus);
        if (st.getEnergy() < cost) return 0.0;

        st.consumeEnergy(cost);
        return bonus;
    }
}