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
        // Manos libres para una técnica de KI: sin ítems reales en ninguna mano. Un arma o un escudo de ki NO
        // estorban (decisión del usuario 2026-10-15): al cargar se ocultan un momento, ver KiWeaponState.
        boolean kiHandsFree,
        // Espada (o arma de ki) en la mano principal: lo que piden las técnicas de espada.
        boolean swordHeld,
        // Hay un movimiento físico CON DURACIÓN en curso (dash, barrage).
        boolean physBusy,
        // Slot de la técnica que se está cargando, o -1 si no hay ninguna.
        int chargingSlot,
        // Ticks transcurridos desde que empezó esa carga. 0 si chargingSlot < 0.
        long chargeElapsedTicks,
        // Está atrapado en un choque de haces (inmóvil, sin poder actuar).
        boolean clash,
        // Aturdido (StunState): no puede actuar mientras dure.
        boolean stunned,
        // Lleva un cuerpo en brazos (estilo CarryOn): las manos están ocupadas y no puede pelear ni transformarse.
        boolean carryingBody
) {
    public boolean chargingTechnique() { return chargingSlot >= 0; }

    /** Copia con el aturdimiento sustituido. Lo usa la evasiva de un jugador AGARRADO: está aturdido por el agarre,
     *  pero zafarse con una evasiva es la excepción que el diseño le deja. */
    public ActionContext withStunned(boolean stunned) {
        return new ActionContext(raceChosen, combatMode, blocking, downed, handsFree, kiHandsFree, swordHeld,
                physBusy, chargingSlot, chargeElapsedTicks, clash, stunned, carryingBody);
    }
}