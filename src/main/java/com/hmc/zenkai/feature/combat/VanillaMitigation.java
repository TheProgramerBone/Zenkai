package com.hmc.zenkai.feature.combat;

import com.hmc.zenkai.config.ServerConfig;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

/**
 * Puente entre las mecánicas de mitigación VANILLA (armadura, dureza, Protección, Resistencia,
 * absorción) y el pipeline zenkai. Único sitio donde viven estas fórmulas.
 * EL PROBLEMA COMÚN: LivingDamageEvent.Pre se dispara DESPUÉS de que vanilla aplique sus
 * reducciones, y computeAttackDamage descarta ese número para sustituirlo por el daño de STR o
 * de ki. Lo que vanilla había calculado se iba a la basura: un jugador con netherite
 * encantada aguantaba lo mismo que desnudo, y los corazones amarillos de una manzana dorada se
 * dibujaban sin llegar a gastarse nunca. La progresión entera de equipo de Minecraft moría en el
 * instante de elegir raza.
 * EL CRITERIO COMÚN: no recalculamos nada de vanilla — leemos lo que vanilla ya decidió y lo
 * traducimos a la escala del mod. Sale gratis lo que vanilla (u otro mod) meta en esas
 * mecánicas, sin duplicar aquí una sola fórmula.
 */
public final class VanillaMitigation {
    private VanillaMitigation() {}

    // =====================================================================
    // ARMADURA
    // =====================================================================

    /**
     * Multiplicador de daño por la mitigación vanilla del defensor. 1.0 = sin reducción.
     * Va como MULTIPLICADOR, igual que KiInfusion.weaponMultiplier: restar 20 puntos de
     * armadura a un golpe de 20.000 es ruido, multiplicar por 0.63 sigue valiendo a cualquier
     * escala.
     * SE LLAMA UNA SOLA VEZ, AL PRINCIPIO DE onDamage: después de computeAttackDamage el daño
     * entrante ya no existe y el ratio no se puede recuperar.
     */
    public static double armorMultiplier(LivingDamageEvent.Pre e) {
        double w = ServerConfig.vanillaArmorWeight();
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
     *  el daño post-armadura no sirve, porque la armadura se cobra aparte en armorMultiplier. */
    public static double originalDamage(LivingDamageEvent.Pre e) {
        return e.getContainer().getOriginalDamage();   // ⚠ misma API que arriba
    }

    // =====================================================================
    // ABSORCIÓN
    // =====================================================================

    /**
     * Gasta la absorción vanilla como escudo sobre el pool body y devuelve lo que la atraviesa.
     * Se puede hacer sin miedo a cobrar dos veces: para el jugador el pipeline pone
     * setNewDamage(0), así que el código de absorción de vanilla en actuallyHurt corre con daño
     * 0 y no descuenta nada. Aquí somos el único consumidor.
     * LA CONVERSIÓN es proporcional, como lo demás del mod: un punto de absorción vale la
     * misma fracción del pool que un punto de vida vanilla. Una manzana dorada da 4 corazones
     * sobre 20, o sea el 20% de tu body — con PL 300 y con PL 3.000.000. En términos relativos
     * es EXACTAMENTE lo que vale en vanilla, ni más ni menos.
     * Usa getMaxHealth() y no un 20 fijo para que un Health Boost de otro mod no descuadre la
     * escala.
     * SE LLAMA AL FINAL de mitigate, después del suelo de daño, igual que vanilla la aplica al
     * final de actuallyHurt. Que esquive el suelo es deliberado: el suelo existe para que
     * apilar DEF no te vuelva inmune, pero un consumible sí debe poder comerse un golpe entero.
     */
    public static double consumeAbsorption(ServerPlayer sp, ZenkaiCombatStats st, double damage) {
        if (damage <= 0.0) return damage;

        float abs = sp.getAbsorptionAmount();
        if (abs <= 0.0F) return damage;

        float maxHp = sp.getMaxHealth();
        int bodyMax = st.getBodyMax();
        if (maxHp <= 0.0F || bodyMax <= 0) return damage;

        double perPoint = (bodyMax / (double) maxHp) * ServerConfig.absorptionWeight();
        if (perPoint <= 1.0e-9) return damage;

        double shield = abs * perPoint;

        if (shield >= damage) {
            // Aguanta: se gasta solo la parte proporcional y el golpe no llega al body.
            float spent = (float) (damage / perPoint);
            sp.setAbsorptionAmount(Math.max(0.0F, abs - spent));
            return 0.0;
        }

        sp.setAbsorptionAmount(0.0F);
        return damage - shield;
    }
}