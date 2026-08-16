package com.hmc.zenkai.feature.action;

/**
 * Fotografía del estado del jugador en el instante de la petición. Cada lado la construye a
 * su manera —el servidor desde sus almacenes, el cliente desde los suyos— pero AMBOS se la
 * pasan a las mismas reglas.
 * Es la pieza que arregla el problema documentado en el javadoc de CombatModeClientState
 * ("las guardas de aquí deben ESPEJAR las de PhysicalCombatServer.tryExecute"): las guardas
 * dejan de estar duplicadas; lo único que cada lado resuelve por su cuenta es de dónde saca
 * estos booleanos.
 * chargeElapsedTicks es AUTORITATIVO en el servidor (gameTime - startTick). En el cliente es
 * una estimación para pintar la barra; nunca viaja por red como verdad.
 */
public record ActionContext(
        boolean raceChosen,
        boolean combatMode,
        boolean blocking,
        boolean downed,
        boolean handsFree,
        // Hay un movimiento físico CON DURACIÓN en curso (dash, barrage).
        boolean physBusy,
        // Slot de la técnica que se está cargando, o -1 si no hay ninguna.
        int chargingSlot,
        // Ticks transcurridos desde que empezó esa carga. 0 si chargingSlot < 0.
        long chargeElapsedTicks
) {
    public boolean chargingTechnique() { return chargingSlot >= 0; }
}