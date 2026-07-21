package com.hmc.zenkai.client;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.content.item.ModDataComponents;
import com.hmc.zenkai.content.item.ModItems;
import com.hmc.zenkai.content.item.special.DragonRadarItem;
import net.minecraft.client.renderer.item.CompassItemPropertyFunction;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Registro de ItemProperties del cliente. El radar usa la MISMA función que la brújula
 * vanilla: el server solo escribe la posición objetiva (componente radar_target) cuando
 * cambia, y el cliente calcula el ángulo cada frame con el suavizado (CompassWobble)
 * incluido. Sin objetivo, la aguja gira al azar sola.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public final class ZenkaiClientSetup {
    private ZenkaiClientSetup() {}

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> ItemProperties.register(
                ModItems.DRAGON_BALL_RADAR.get(),
                ResourceLocation.withDefaultNamespace("angle"),
                new CompassItemPropertyFunction((clientLevel, stack, entity) ->
                        stack.get(ModDataComponents.RADAR_TARGET.get()))));

        ItemProperties.register(
                ModItems.DRAGON_BALL_RADAR.get(),
                ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "active"),
                (stack, clientLevel, entity, seed) ->
                        DragonRadarItem.isActive(stack) ? 1.0F : 0.0F);
    }
}