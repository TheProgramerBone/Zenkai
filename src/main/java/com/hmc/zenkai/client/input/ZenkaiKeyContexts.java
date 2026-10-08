package com.hmc.zenkai.client.input;

import com.hmc.zenkai.client.CombatModeClientState;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.settings.IKeyConflictContext;

/**
 * Contextos de conflicto de teclas propios del mod. Un KeyMapping solo se considera "pulsado" (y
 * solo choca con otros) cuando su contexto está activo.
 *
 * COMBAT_MODE: activo únicamente con el modo combate encendido y sin ninguna pantalla abierta. Las
 * teclas de slot de técnica usan este contexto, así que pueden compartir tecla (1-9) con la hotbar
 * vanilla sin que la pantalla de Controles marque conflicto: nunca están activas a la vez que la
 * hotbar normal. Choca solo consigo mismo: dos slots del modo combate en la misma tecla SÍ se avisan.
 */
public final class ZenkaiKeyContexts {
    private ZenkaiKeyContexts() {}

    public static final IKeyConflictContext COMBAT_MODE = new IKeyConflictContext() {
        @Override
        public boolean isActive() {
            return CombatModeClientState.isActive() && Minecraft.getInstance().screen == null;
        }

        @Override
        public boolean conflicts(IKeyConflictContext other) {
            return this == other;
        }
    };
}
