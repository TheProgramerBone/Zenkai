package com.hmc.zenkai.feature.kiweapon;

/**
 * Números de un arma de ki. Vienen de datapack (data/&lt;ns&gt;/zenkai_ki_weapons/&lt;id&gt;.json)
 * para poder rebalancear en caliente con /reload, igual que las técnicas y las formas.
 * Lo que NO está aquí y sí en el registro del item: alcance y velocidad de ataque. Esos van
 * como modificadores de atributo del propio item, que es como los lleva vanilla; gestionarlos
 * a mano habría significado poner y quitar modificadores al equipar, desequipar, morir y
 * cambiar de dimensión — justo la clase de estado repartido que ya nos costó el bug del
 * attack_speed infinito.
 *
 * @param damageMult  multiplicador sobre el daño STR, sustituyendo al del arma vanilla
 * @param kiCostMult  cuánto encarece el ki de este golpe (1.0 = igual que a mano vacía)
 */
public record KiWeaponDef(double damageMult, double kiCostMult) {

    /** Lo que se usa si falta el JSON: arma inofensiva pero funcional, nunca un crash. */
    public static final KiWeaponDef FALLBACK = new KiWeaponDef(1.0, 1.0);
}