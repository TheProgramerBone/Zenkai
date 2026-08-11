package com.hmc.zenkai.content.blockentity;

/**
 * Por qué está parado un trabajo que sigue vivo. Sustituye al booleano `paused`: la pausa
 * tenía una sola causa (jugador ausente o sin materiales) y ahora hay tres, así que la GUI
 * necesita saber CUÁL para no mentir — decir "faltan materiales" cuando lo que falta es
 * corriente manda al jugador a buscar lo que no es.
 * Viaja como entero por el índice 3 del ContainerData, que es donde iba el booleano, así que
 * no cuesta un hueco nuevo de sincronización.
 * NO hay un motivo por trabajo cancelado: cancelar destruye el trabajo, no lo pausa.
 */
public enum PauseReason {
    /** Trabajando de verdad. */
    NONE("none"),
    /** El que lo empezó se desconectó. El progreso espera a que vuelva. */
    OWNER("owner"),
    /** Le faltan materiales en el inventario. Se cobran al terminar, así que puede gastarlos. */
    MATERIALS("materials"),
    /** Búfer de FE vacío. */
    ENERGY("energy");

    private final String key;

    PauseReason(String key) { this.key = key; }

    /** Clave de idioma: screen.zenkai.scouter_bench.paused.<motivo>. */
    public String langKey() { return "screen.zenkai.scouter_bench.paused." + key; }

    public boolean isPaused() { return this != NONE; }

    private static final PauseReason[] VALUES = values();

    /** Desde el entero del ContainerData. Fuera de rango cae en NONE, nunca revienta. */
    public static PauseReason byId(int id) {
        return (id >= 0 && id < VALUES.length) ? VALUES[id] : NONE;
    }
}