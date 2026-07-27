package com.hmc.zenkai.feature.combat;

import net.minecraft.world.entity.LivingEntity;

/**
 * Contrato común de combate que implementan TANTO el jugador (PlayerStatsAttachment)
 * COMO las entidades (EntityStats). El pipeline de combate hablará solo con esta interfaz,
 * así no le importa si golpea/recibe un jugador o un mob.
 *
 * Los stats "final" son los derivados lineales (atributo × multiplicadores). El Power Level
 * se calcula UNA sola vez, con la fórmula de {@link PowerLevel}, vía el default.
 *
 * FASE 1: solo el modelo de datos. Nada de esto se engancha al combate todavía.
 */
public interface ZenkaiCombatStats {

    // ── Stats derivados (lineales) ───────────────────────────────────────────
    double computeMeleeFinal();    // STR
    double computeDefenseFinal();  // DEX
    double computeKiPowerFinal();  // WIL
    double computeKiPoolFinal();   // SPI
    double computeConFinal();      // CON (lineal, sin el offset del pool)

    /** ¿Participa del combate Zenkai? Jugador: raza elegida. Entidad: stats resueltos. */
    boolean isCombatActive();

    // ── Pool de vida real (body). La vida vanilla queda cosmética. ───────────
    int  getBody();
    int  getBodyMax();
    void addBody(int delta);

    // ── Stamina / Energía (ki) ───────────────────────────────────────────────
    int  getStamina();
    int  getStaminaMax();
    void consumeStamina(int amount);
    int  getEnergy();
    int  getEnergyMax();

    // ── Power Level: derivado, mismo cálculo para jugador y entidad ───────────
    default long getPowerLevel() {
        return PowerLevel.compute(this);
    }

    /**
     * Refleja el pool del mod en la vida vanilla conservando el RATIO.
     * Sin esto, las entidades con lógica propia sobre getHealth() (dragón, wither, warden)
     * mantenían la barra llena mientras su body bajaba en paralelo: sus fases y su muerte
     * leen la vida vanilla, no nuestro pool.
     * Va en la interfaz y no en EntityStats porque el pipeline de daño trabaja con este tipo,
     * y porque el jugador necesita lo mismo (ImmortalityEffect ya lo hacía a mano).
     * No baja de 1: matar es cosa del pipeline de daño, no de este espejo — si el body llega
     * a 0, applyToZenkaiVictim deja pasar el golpe letal de verdad.
     */
    default void mirrorToVanilla(LivingEntity le) {
        int max = getBodyMax();
        if (max <= 0) return;
        float target = le.getMaxHealth() * (getBody() / (float) max);
        le.setHealth(Math.max(1.0F, Math.min(le.getMaxHealth(), target)));
    }
}