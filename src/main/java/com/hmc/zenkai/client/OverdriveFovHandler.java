package com.hmc.zenkai.client;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.overlay.OverdriveClientState;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ComputeFovModifierEvent;

/**
 * Zoom leve de cámara mientras se sostiene Shift+cargar forzando el 100% — el mismo espíritu
 * de "algo intenso está pasando" que un punch de transformación, pero deliberadamente sutil (a
 * petición: nada agresivo) y SIN mover la cámara (el shake se descartó, ver CameraShakeMixin).
 * Reusa la condición ya compartida con el jitter del gauge (OverdriveClientState), con su
 * propia rampa de entrada/salida para que el zoom no sea un salto brusco.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public final class OverdriveFovHandler {
    private OverdriveFovHandler() {}

    @SubscribeEvent
    public static void onComputeFovModifier(ComputeFovModifierEvent e) {
        Player p = e.getPlayer();
        if (p.level() == null) return;
        float mult = OverdriveClientState.fovMultiplier(p.level().getGameTime());
        if (mult != 1f) e.setNewFovModifier(e.getNewFovModifier() * mult);
    }
}
