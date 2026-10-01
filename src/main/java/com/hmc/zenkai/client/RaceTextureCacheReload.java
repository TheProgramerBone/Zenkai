package com.hmc.zenkai.client;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.feature.race.RaceTextureUtil;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;

/**
 * Al recargar recursos del cliente (F3+T, cambio de resource pack) invalida lo que se cacheó
 * sobre texturas y modelos: la caché de RaceTextureUtil.resourceExists y la del origen de la
 * cara por modelo de FaceThumbnails.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public final class RaceTextureCacheReload {
    private RaceTextureCacheReload() {}

    @SubscribeEvent
    public static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) rm -> {
            RaceTextureUtil.clearCache();
            com.hmc.zenkai.client.gui.FaceThumbnails.clearCache();
        });
    }
}
