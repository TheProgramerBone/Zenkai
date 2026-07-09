package com.hmc.zenkai.core.combat;

import com.hmc.zenkai.core.network.feature.Dbrattributes;

import java.util.EnumMap;

/**
 * Power Level = SUMA PONDERADA LINEAL de los stats de combate (predecible; el daño NO depende del PL).
 * MIND queda fuera (es para habilidades). Una sola fórmula para jugador y entidades.
 *
 * Los pesos por ATRIBUTO fijan la escala del PL (tuneables en Fase 4 / config):
 *   PL = wSTR·melee + wCON·con + wDEX·defensa + wWIL·kiPower + wSPI·kiPool
 *
 * El back-solver invierte esta misma fórmula: dado un PL objetivo + la "forma" (shape) de un
 * arquetipo, reparte los atributos para que el PL calculado dé justo ese objetivo.
 */
public final class PowerLevel {
    private PowerLevel() {}

    // Pesos por atributo. Todos 0.1 por defecto -> un humano base (attrs 10) ≈ PL 5.
    // Súbelos/diferéncialos en el rebalanceo (p. ej. ofensiva pesa más).
    public static final double W_STR = 0.1; // melee
    public static final double W_CON = 0.1; // body
    public static final double W_DEX = 0.1; // defensa
    public static final double W_WIL = 0.1; // ki power
    public static final double W_SPI = 0.1; // ki pool

    /** PL a partir de cualquier portador de stats (jugador o entidad). */
    public static long compute(ZenkaiCombatStats s) {
        double pl = W_STR * s.computeMeleeFinal()
                + W_CON * s.computeConFinal()
                + W_DEX * s.computeDefenseFinal()
                + W_WIL * s.computeKiPowerFinal()
                + W_SPI * s.computeKiPoolFinal();
        return Math.max(0L, Math.round(pl));
    }

    /**
     * Reparte atributos para alcanzar {@code targetPL} siguiendo la forma del arquetipo.
     * Como (para entidades) el stat efectivo = atributo × 1, se cumple PL = Σ w·attr, y con
     * attr = k·shape queda k = targetPL / Σ(w·shape). Cerrado y exacto. MIND se deja en 0.
     */
    public static EnumMap<Dbrattributes, Integer> solveAttributes(long targetPL, Archetype arch) {
        double denom =
                W_STR * arch.shape(Dbrattributes.STRENGTH)
                        + W_CON * arch.shape(Dbrattributes.CONSTITUTION)
                        + W_DEX * arch.shape(Dbrattributes.DEXTERITY)
                        + W_WIL * arch.shape(Dbrattributes.WILLPOWER)
                        + W_SPI * arch.shape(Dbrattributes.SPIRIT);

        double k = (denom <= 0) ? 0 : targetPL / denom;

        EnumMap<Dbrattributes, Integer> out = new EnumMap<>(Dbrattributes.class);
        out.put(Dbrattributes.STRENGTH,     round(k * arch.shape(Dbrattributes.STRENGTH)));
        out.put(Dbrattributes.CONSTITUTION, round(k * arch.shape(Dbrattributes.CONSTITUTION)));
        out.put(Dbrattributes.DEXTERITY,    round(k * arch.shape(Dbrattributes.DEXTERITY)));
        out.put(Dbrattributes.WILLPOWER,    round(k * arch.shape(Dbrattributes.WILLPOWER)));
        out.put(Dbrattributes.SPIRIT,       round(k * arch.shape(Dbrattributes.SPIRIT)));
        out.put(Dbrattributes.MIND,         0); // MIND = habilidades, no entra en PL ni en el reparto
        return out;
    }

    private static int round(double v) {
        return (int) Math.max(0, Math.round(v));
    }
}