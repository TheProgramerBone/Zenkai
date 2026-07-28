package com.hmc.zenkai.feature.combat;

import com.hmc.zenkai.config.CommonConfig;
import com.hmc.zenkai.content.item.KiWeaponItem;
import com.hmc.zenkai.feature.kiweapon.KiWeaponServer;
import com.hmc.zenkai.feature.skills.SkillEffects;
import com.hmc.zenkai.feature.skills.SkillToggles;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

/**
 * Ki Infuse y el ARMA COMO MULTIPLICADOR. Único sitio donde viven las dos fórmulas, para que
 * el melee, los proyectiles (paso 2b) y el arma de ki lean lo mismo.
 * EL PROBLEMA QUE RESUELVE EL MULTIPLICADOR: el pipeline sumaba el daño del arma al de STR
 * (strDamage + weaponBonus). Con STR en cientos o miles, una espada de diamante aportaba ~8
 * puntos: ruido estadístico. Empuñar netherita o un palo daba lo mismo. Como MULTIPLICADOR,
 * el arma vale un % del golpe y por tanto sigue importando con STR 10 y con STR 200.000, sin
 * recalibrar nada — el mismo criterio autoescalable que ya usa la defensa porcentual.
 * Esto se aplica SIEMPRE, tenga o no el jugador la habilidad: es el arreglo del sistema base.
 * LA INFUSIÓN, en cambio, SUMA (no multiplica) y sale de WIL:
 *   bonus = computeKiPowerFinal() * curva[nivel]
 * Sumar y no multiplicar es deliberado: multiplicando, el que ya pega fuerte gana más aún y
 * el ki user (poco STR, mucho WIL) seguiría sin poder pelear de cerca, que es justo a quien
 * va dirigida la habilidad. computeKiPowerFinal ya lleva dentro el % de poder y el
 * multiplicador de forma, así que la infusión escala con transformaciones sin código extra.
 * El coste se paga sobre el BONUS, no sobre el golpe entero: pagas por lo que la habilidad
 * añade, no por el puñetazo que ya sabías dar.
 */
public final class KiInfusion {
    private KiInfusion() {}

    /** Daño de ataque vanilla total: 1.0 de base + arma + modificadores (pociones, etc.). */
    public static double attackDamageOf(LivingEntity e) {
        AttributeInstance attr = e.getAttribute(Attributes.ATTACK_DAMAGE);
        return attr == null ? 1.0 : attr.getValue();
    }

    /** ¿Empuña algo que cuente como arma? (cualquier cosa por encima del puño desnudo) */
    public static boolean hasWeapon(LivingEntity e) {
        return attackDamageOf(e) > 1.0 + 1.0e-6;
    }

    /**
     * Daño extra bruto de la infusión, SIN escalar por la carga del golpe. 0 si el
     * interruptor está apagado o si va con las manos vacías: infusionar exige un arma, o
     * Ki Infuse sería un Ki Fist barato y las dos habilidades se pisarían.
     */
    public static double rawMeleeBonus(Player p, ZenkaiCombatStats st) {
        if (!SkillToggles.isOn(p, SkillEffects.KI_INFUSE)) return 0.0;
        if (!hasWeapon(p)) return 0.0;
        double f = SkillEffects.kiInfuseFactor(p);
        return f <= 0.0 ? 0.0 : st.computeKiPowerFinal() * f;
    }

    /** Ki que cuesta un bonus dado. El multiplicador de raza reparte usos por barra llena. */
    public static int kiCost(ZenkaiCombatStats st, double bonus) {
        if (bonus <= 0.0) return 0;
        return (int) Math.max(1, Math.ceil(
                bonus * CommonConfig.kiPerBonusDamage() * st.kiCostMult()));
    }

    /**
     * Melee: calcula el bonus, COBRA el ki y devuelve el bonus ya escalado por la carga.
     * CAÍDA SILENCIOSA: sin ki suficiente devuelve 0 y no cobra nada — el golpe sale como
     * un melee normal con arma en vez de fallar o quedarse a medias.
     * Cobrar aquí dentro y no en quien llama es a propósito: coste y daño salen del mismo
     * número, así que no pueden descuadrarse si alguien toca uno de los dos.
     */
    public static double spendForMelee(Player p, ZenkaiCombatStats st, double chargeF) {
        double bonus = rawMeleeBonus(p, st) * chargeF;
        if (bonus <= 0.0) return 0.0;

        int cost = kiCost(st, bonus);
        if (st.getEnergy() < cost) return 0.0;

        st.consumeEnergy(cost);
        return bonus;
    }

    public static double weaponMultiplier(LivingEntity e) {
        // El arma de ki no usa la fórmula del attack_damage: su multiplicador es un número
        // de datapack, porque no representa "una hoja mejor" sino ki moldeado.
        if (e instanceof Player p) {
            KiWeaponItem w = KiWeaponServer.heldWeapon(p);
            if (w != null) return w.def().damageMult();
        }
        double extra = Math.max(0.0, attackDamageOf(e) - 1.0);
        return 1.0 + extra * CommonConfig.weaponScale();
    }

    /** Coste en ki del EXTRA que aporta el arma de ki sobre pegar a mano limpia. Se cobra
    *  aparte porque, si no, el multiplicador saldría gratis y no habría decisión que tomar
    *  entre invocarla o no. */
    public static double kiWeaponExtra(Player p, double strDamage, double chargeF) {
        KiWeaponItem w = KiWeaponServer.heldWeapon(p);
        return w == null ? 0.0 : strDamage * chargeF * Math.max(0.0, w.def().damageMult() - 1.0);
    }
}