package com.hmc.zenkai.feature.kiweapon;

/**
 * Números de un arma de ki. Vienen de datapack (data/&lt;ns&gt;/zenkai_ki_weapons/&lt;id&gt;.json)
 * para poder rebalancear en caliente con /reload, igual que las técnicas y las formas.
 * El arma ya no es un ítem, así que alcance, velocidad y daño de ataque también viven aquí: el
 * servidor los aplica como modificadores TRANSITORIOS mientras el arma está activa (ver
 * {@link KiWeaponServer}) y los retira al desvanecerse, por lo que ningún estado sobrevive a un
 * cierre anómalo del servidor.
 *
 * @param damageMult   multiplicador sobre el daño STR, sustituyendo al del arma vanilla
 * @param kiCostMult   cuánto encarece el ki de este golpe (1.0 = igual que a mano vacía)
 * @param reach        alcance extra de interacción con entidades (bloques)
 * @param attackSpeed  modificador ADD_VALUE de attack_speed (negativo = más lento)
 * @param attackDamage modificador ADD_VALUE de attack_damage; solo importa para que
 *                     {@code KiInfusion.hasWeapon} vea "un arma" (el daño real sale de damageMult)
 * @param twoHanded    true = arma de dos manos. Desde 2026-10-15 ya NO quita el escudo de ki (convive con
 *                     cualquier arma de ki, decisión del usuario); se conserva para los datapacks y como dato
 * @param knockback    empuje extra al golpear (0 = el de vanilla). Lo aplica KiWeaponServer
 * @param guardBreak   0..1: fracción de la reducción de daño de la GUARDIA del rival que este arma
 *                     ignora (el hacha rompe guardias)
 * @param blockBonus   solo escudo: reducción de daño EXTRA al bloquear, sumada a la de la guardia
 * @param blockKiPct   solo escudo: fracción del ki máximo que cuesta cada golpe bloqueado
 */
public record KiWeaponDef(double damageMult, double kiCostMult,
                          double reach, double attackSpeed, double attackDamage,
                          boolean twoHanded, double knockback, double guardBreak,
                          double blockBonus, double blockKiPct) {

    /** Lo que se usa si falta el JSON: arma inofensiva pero funcional, nunca un crash. */
    public static final KiWeaponDef FALLBACK =
            new KiWeaponDef(1.0, 1.0, 0.0, 0.0, 2.0, false, 0.0, 0.0, 0.0, 0.0);
}
