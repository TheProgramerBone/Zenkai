package com.hmc.zenkai.feature.combat;

/**
 * Cómo se activa el parry (ServerConfig parry.trigger):
 *  - BLOCK: solo al empezar a bloquear. Bloquear pulsa «parry» primero y la guardia real
 *    arranca pasada la ventana (ver ParryServer.deferBlock).
 *  - KEY: solo con la tecla de parry; bloquear es siempre una guardia inmediata.
 *  - BOTH: las dos formas.
 */
public enum ParryTrigger {
    BLOCK, KEY, BOTH;

    public boolean allowsBlock() { return this != KEY; }

    public boolean allowsKey() { return this != BLOCK; }
}
