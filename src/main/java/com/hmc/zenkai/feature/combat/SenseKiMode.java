package com.hmc.zenkai.feature.combat;

/**
 * Modos del "sentir el ki" (ciclo con F4, empieza y termina en OFF):
 * OFF -> ALL -> PLAYERS -> MOBS -> PLAYERS_STRONG -> MOBS_STRONG -> LOCKED -> OFF
 * Cada cambio se anuncia en la actionbar (messages.zenkai.sense_ki.<id>).
 */

public enum SenseKiMode {
    OFF("off"),
    ALL("all"),
    PLAYERS("players"),
    MOBS("mobs"),
    PLAYERS_STRONG("players_strong"),
    MOBS_STRONG("mobs_strong"),
    /** Solo el objetivo fijado con el lock-on. El id NO es "locked": esa clave ya la usa
     *  el aviso de "no tienes la habilidad" en onKeyPress. */
    LOCKED("only_locked");

        private final String id;

        SenseKiMode(String id) { this.id = id; }

        public String translationKey() { return "messages.zenkai.sense_ki." + id; }

        public SenseKiMode next() {
            SenseKiMode[] v = values();
            return v[(ordinal() + 1) % v.length];
        }
}