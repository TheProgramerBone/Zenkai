package com.hmc.zenkai.feature.action;

/**
 * Resultado de una comprobación de ActionRules. OK = adelante; el resto identifica por qué no.
 * El motivo importa: el cliente lo usa para NO gastar cooldown local en algo que el servidor
 * va a rechazar, y en la fase 5 decidirá con qué transición cortar la animación.
 */
public enum ActionReject {
    OK,
    NO_RACE,
    NOT_COMBAT_MODE,
    DOWNED,
    BLOCKING,
    HANDS_BUSY,
    DISABLED,        // técnica sin JSON
    LOCKED,          // no desbloqueada
    ON_COOLDOWN,
    NO_RESOURCE,     // estamina o ki insuficiente
    BUSY,            // otra acción exclusiva en curso
    NO_CHARGE,       // release sin carga en curso
    WRONG_SLOT,      // release de un slot distinto al que cargaba
    UNDERCHARGED;    // por debajo de MIN_CHARGE

    public boolean ok() { return this == OK; }
}