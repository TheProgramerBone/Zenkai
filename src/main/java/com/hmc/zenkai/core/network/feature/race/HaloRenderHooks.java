package com.hmc.zenkai.core.network.feature.race;

import com.hmc.zenkai.Zenkai;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * Añade HaloGeoLayer a cada renderer de jugador. Separado de RaceSkinRenderHooks
 * para no tocar ese archivo; el halo se dibuja encima (flota sobre la cabeza).
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public final class HaloRenderHooks {
    private HaloRenderHooks() {}

    @SubscribeEvent
    public static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        var mc = Minecraft.getInstance();
        for (PlayerSkin.Model skin : PlayerSkin.Model.values()) {
            PlayerRenderer renderer = event.getSkin(skin);
            if (renderer == null) continue;
            renderer.addLayer(new HaloGeoLayer(renderer, event.getEntityModels(), mc.getModelManager()));
        }
    }
}