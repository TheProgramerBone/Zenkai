package com.hmc.zenkai.client;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.aura.AuraEmberRenderer;
import com.hmc.zenkai.client.aura.AuraRimRenderer;
import com.hmc.zenkai.client.aura.AuraSparkRenderer;
import com.hmc.zenkai.client.aura.AuraTiltController;
import com.hmc.zenkai.client.aura.AuraTrailRenderer;
import com.hmc.zenkai.client.aura.AuraWispRenderer;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

/**
 * Limpieza del estado VISUAL por jugador cuando el LocalPlayer se reemplaza (reaparecer tras
 * morir, cambiar de dimensión) — pedido del usuario 2026-09-24: "al reaparecer, los efectos de
 * oscuridad y los problemas de renderizado de cámara persisten".
 * <p>
 * POR QUÉ HACÍA FALTA. Al reaparecer, el servidor conserva el MISMO id de entidad para el
 * jugador, y el cliente guarda una docena de mapas estáticos por ese id (estela, lenguas,
 * chispas, ascuas, inclinación y rampa del rim del aura; carga/desvanecido de ki). Nada los
 * vaciaba en ese momento: la estela del aura, por ejemplo, conservaba posiciones del sitio donde
 * moriste y tendía una cinta hasta el spawn, y la rampa del rim seguía a media intensidad. La
 * única limpieza existente (AuraRenderer.onStopTracking) cuelga de {@code PlayerEvent.StopTracking},
 * un evento de SERVIDOR: en un servidor dedicado no se dispara nunca en el cliente, y en un
 * mundo de un jugador corre en el hilo del servidor.
 * <p>
 * Se limpian el id viejo y el nuevo (coinciden casi siempre, pero no es contrato de vanilla).
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public final class ClientVfxStateReset {
    private ClientVfxStateReset() {}

    @SubscribeEvent
    public static void onPlayerClone(ClientPlayerNetworkEvent.Clone e) {
        forget(e.getOldPlayer().getId());
        forget(e.getNewPlayer().getId());
    }

    private static void forget(int id) {
        // Los mapas del aura son HashMap sin sincronizar: se tocan SIEMPRE en el hilo de render.
        Minecraft.getInstance().execute(() -> {
            AuraTiltController.clear(id);
            AuraTrailRenderer.clear(id);
            AuraWispRenderer.clear(id);
            AuraSparkRenderer.clear(id);
            AuraEmberRenderer.clear(id);
            AuraRimRenderer.clear(id);
            KiChargeClientState.forget(id);
        });
    }
}
