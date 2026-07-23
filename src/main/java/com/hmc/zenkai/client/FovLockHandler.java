package com.hmc.zenkai.client;

import com.hmc.zenkai.Zenkai;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ComputeFovModifierEvent;

/**
 * El FOV no reacciona a la velocidad: se queda en el que el jugador eligió.
 * Vanilla deforma el FOV con la velocidad de movimiento, y con los multiplicadores de forma
 * (SSJ4 vuela a ~9x) eso da un efecto ojo de pez constante y mareante. NO hace falta mixin:
 * NeoForge expone justo el punto donde vanilla calcula ese modificador.
 * Nota: vanilla ya trae la opción "FOV Effects" a 0 para lo mismo. Esto lo fuerza siempre,
 * porque el efecto que produce este mod no es el que el jugador aceptó al dejarla activada.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public final class FovLockHandler {
    private FovLockHandler() {}

    @SubscribeEvent
    public static void onComputeFovModifier(ComputeFovModifierEvent e) {
        e.setNewFovModifier(1.0F);
    }
}