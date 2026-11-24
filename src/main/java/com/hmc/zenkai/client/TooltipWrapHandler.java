package com.hmc.zenkai.client;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.gui.TooltipText;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;

/**
 * Fija el ancho máximo de los tooltips que arma NeoForge (ítems y {@code renderComponentTooltip}) para que una línea
 * larga se parta en renglones en vez de ocupar toda la pantalla. Solo toca lo del mod: los ítems de {@code zenkai} y
 * los tooltips hechos con una pantalla del mod abierta; el resto de mods e ítems vanilla quedan como están.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public final class TooltipWrapHandler {
    private TooltipWrapHandler() {}

    @SubscribeEvent
    public static void onGather(RenderTooltipEvent.GatherComponents event) {
        if (!isOurs(event)) return;
        int w = TooltipText.maxWidth(event.getScreenWidth());
        if (event.getMaxWidth() < 0 || event.getMaxWidth() > w) event.setMaxWidth(w);
    }

    private static boolean isOurs(RenderTooltipEvent.GatherComponents event) {
        if (!event.getItemStack().isEmpty()
                && Zenkai.MOD_ID.equals(BuiltInRegistries.ITEM.getKey(event.getItemStack().getItem()).getNamespace())) {
            return true;
        }
        var screen = Minecraft.getInstance().screen;
        return screen != null && screen.getClass().getName().startsWith("com.hmc.zenkai.");
    }
}
