package com.hmc.zenkai.feature.combat;

import com.hmc.zenkai.config.ServerConfig;
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
                bonus * ServerConfig.kiPerBonusDamage() * st.kiCostMult()));
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
        return 1.0 + extra * ServerConfig.weaponScale();
    }

    /**
     * BONUS DE DAÑO DE VANILLA COMO MULTIPLICADOR, RELATIVO igual que projectileMultiplier (no
     * absoluto como weaponMultiplier). Cubre Filo/Aspereza/Perjuicio de los Artrópodos, el bonus
     * de la maza por distancia de caída (Densidad y Grieta incluidos — vanilla lo suma al mismo
     * float, vía {@code Item.getAttackDamageBonus}, ANTES del crítico), y cualquier
     * encantamiento/atributo de OTRO MOD que toque ese mismo número: no hace falta listarlos uno
     * a uno, se lee la PROPORCIÓN que vanilla ya calculó, igual que armorMultiplier lee la
     * proporción de la armadura en vez de recalcularla.
     * Fuerza/Debilidad NO se cuentan aparte porque ya viven en el propio atributo ATTACK_DAMAGE
     * (attackDamageOf las incluye), así que el cociente de abajo las cancela solo.
     * <p>
     * {@code mult = 1 + (originalVanillaDamage / (attackDamageOf(e) * critMultiplier) - 1) * scale}
     * <p>
     * Con scale 1.0, Filo V vale aquí lo mismo que vale en el golpe vanilla; con una maza
     * cayendo de mucha altura el cociente crece con la altura porque el numerador (daño vanilla
     * ya con el bonus de caída sumado) crece y el denominador no — el golpe STR también crece
     * con la altura sin que este archivo sepa qué es una maza.
     *
     * @param originalVanillaDamage daño ANTES de armadura (VanillaMitigation.originalDamage) —
     *        ya incluye crítico, encantamientos y cualquier bonus situacional del arma.
     * @param critMultiplier el mismo multiplicador de CriticalHitEvent que CombatZenkaiHooks ya
     *        le aplica a `total` — hay que descontarlo aquí o el crítico se contaría dos veces.
     */
    public static double enchantMultiplier(LivingEntity e, double originalVanillaDamage,
                                           double critMultiplier) {
        double scale = ServerConfig.meleeEnchantScale();
        double baseline = attackDamageOf(e) * Math.max(critMultiplier, 1.0e-6);
        if (scale <= 0.0 || baseline <= 0.0 || originalVanillaDamage <= 0.0) return 1.0;

        double mult = 1.0 + (originalVanillaDamage / baseline - 1.0) * scale;
        return Math.max(0.0, Math.min(mult, ServerConfig.meleeEnchantMultCap()));
    }

    /**
     * PROYECTIL COMO MULTIPLICADOR. Hermano de weaponMultiplier, pero RELATIVO y no absoluto,
     * y esa diferencia es el arreglo: attack_damage en vanilla llega a ~10 y admite una escala
     * pequeña, pero el daño de una flecha va de 6 a 9 y con escala absoluta Poder V — un
     * encantamiento de nivel máximo — valía un +30% de nada. En relativo la regla es una frase:
     * un encantamiento del arco aporta a la infusión la misma proporción que le aporta a la
     * flecha. Con scale 1.0, Poder V vale x1.50 aquí igual que vale x1.50 allí, y si Mojang
     * retoca la fórmula de Poder esto no se entera.
     * Solo escala el BONUS. El daño vanilla del proyectil ya va sumado aparte en
     * computeAttackDamage; multiplicarlo también sería cobrar el mismo encantamiento dos veces.
     * El cap no es decorativo: un arco de otro mod con 30 de daño daría x5 sobre un bonus de
     * cuatro cifras. Por abajo NO hay suelo a propósito — media carga da x0.5, que es el mismo
     * castigo al spam que aplica chargeF en melee.
     *
     * @param originalVanillaDamage daño del proyectil ANTES de armadura (VanillaArmor.originalDamage).
     */
    public static double projectileMultiplier(double originalVanillaDamage) {
        double base = ServerConfig.projectileBaseDamage();
        if (base <= 0.0 || originalVanillaDamage <= 0.0) return 1.0;

        double mult = 1.0 + (originalVanillaDamage / base - 1.0) * ServerConfig.projectileScale();
        return Math.max(0.0, Math.min(mult, ServerConfig.projectileMultCap()));
    }

    /** Coste en ki del EXTRA que aporta el arma de ki sobre pegar a mano limpia. Se cobra
    *  aparte porque, si no, el multiplicador saldría gratis y no habría decisión que tomar
    *  entre invocarla o no. */
    public static double kiWeaponExtra(Player p, double strDamage, double chargeF) {
        KiWeaponItem w = KiWeaponServer.heldWeapon(p);
        return w == null ? 0.0 : strDamage * chargeF * Math.max(0.0, w.def().damageMult() - 1.0);
    }
}