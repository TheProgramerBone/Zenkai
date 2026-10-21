package com.hmc.zenkai.feature.combat.entity;

/**
 * Una fase de transformación de un NPC de combate, de la lista "transformations" de su ficha JSON
 * (data/&lt;ns&gt;/zenkai_entities/*.json):
 * <pre>
 * "transformations": [
 *   { "at_health_pct": 70, "power_mult": 1.5, "duration_ticks": 60, "heal_pct": 10 },
 *   { "at_health_pct": 40, "power_mult": 2.5, "duration_ticks": 80 }
 * ]
 * </pre>
 * Cada fase se dispara UNA vez, en orden, cuando el mob está en combate y su vida baja de
 * {@code atHealthPct}. Durante {@code durationTicks} se queda quieto haciendo la animación; al terminar
 * recupera {@code healPct} % de su vida máxima y su PL real pasa a ser el PL BASE de la ficha por
 * {@code powerMult} (siempre sobre el base, no encadenado: cada fase se ajusta sola).
 * Una ficha antigua con un solo objeto "transformation" se lee como una lista de una fase.
 */
public record EntityTransformation(double atHealthPct, double powerMult, int durationTicks, double healPct) {
    public static final int DEFAULT_DURATION_TICKS = 60;

    public boolean valid() {
        return atHealthPct > 0 && atHealthPct < 100 && powerMult > 1.0 && durationTicks > 0
                && healPct >= 0 && healPct <= 100;
    }
}
