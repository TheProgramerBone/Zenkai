package com.hmc.zenkai.core.combat;

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
}