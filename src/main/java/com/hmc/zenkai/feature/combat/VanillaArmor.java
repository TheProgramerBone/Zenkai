package com.hmc.zenkai.feature.combat;

import com.hmc.zenkai.config.CommonConfig;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

/**
 * Puente entre la mitigación VANILLA (armadura, dureza, Protección, Resistencia) y el
 * pipeline zenkai. Único sitio donde vive esta fórmula.
 * EL PROBLEMA: LivingDamageEvent.Pre se dispara DESPUÉS de que vanilla aplique armadura y
 * encantamientos. computeAttackDamage descarta ese número y lo sustituye por el daño de STR
 * o de ki, así que la reducción se tiraba a la basura: un jugador con netherite encantada
 * aguantaba lo mismo que desnudo. Toda la progresión de armadura de Minecraft moría en el
 * instante de elegir raza. (Incoherente además con EntityStats.applyVanilla, que SÍ lee
 * Attributes.ARMOR para los mobs.)
 * LA SOLUCIÓN: no recalculamos la armadura — leemos el RATIO que vanilla acaba de aplicar y
 * lo reusamos como MULTIPLICADOR sobre el daño zenkai. Mismo criterio autoescalable que
 * KiInfusion.weaponMultiplier: restar 20 puntos de armadura a un golpe de 20.000 es ruido,
 * multiplicar por 0.63 sigue valiendo a cualquier escala. Y sale gratis lo que vanilla
 * (u otro mod) meta en esa reducción, sin duplicar su fórmula aquí.
 * El peso de config existe porque el ratio crudo es brutal: netherite + Protección IV corta
 * el 91%. Con weight 0.5 corta el 45%, que es fuerte pero no obligatorio.
 */
public final class VanillaArmor {
    private VanillaArmor() {}

    /**
     * Multiplicador de daño por la mitigación vanilla del defensor. 1.0 = sin reducción.
     * SE LLAMA UNA SOLA VEZ, AL PRINCIPIO DE onDamage: después de computeAttackDamage el
     * daño entrante ya no existe y el ratio no se puede recuperar.
     */
    public static double multiplier(LivingDamageEvent.Pre e) {
        double w = CommonConfig.vanillaArmorWeight();
        if (w <= 0.0) return 1.0;

        // ⚠ API a verificar al compilar: LivingDamageEvent.Pre#getContainer() y
        // DamageContainer#getOriginalDamage() en NeoForge 1.21.1. Si Pre expone
        // getOriginalDamage() directamente, usa esa y quita el getContainer().
        double original = e.getContainer().getOriginalDamage();
        if (original <= 1.0e-6) return 1.0;

        double ratio = e.getNewDamage() / original;
        if (!Double.isFinite(ratio)) return 1.0;
        ratio = Math.max(0.0, Math.min(1.0, ratio));

        return (1.0 - w) + w * ratio;
    }

    /** Daño vanilla ANTES de cualquier reducción. Es la referencia del arco para Ki Infuse:
     *  el daño post-armadura no sirve, porque la armadura se cobra otra vez en multiplier(). */
    public static double originalDamage(LivingDamageEvent.Pre e) {
        return e.getContainer().getOriginalDamage();   // ⚠ misma API que arriba
    }
}