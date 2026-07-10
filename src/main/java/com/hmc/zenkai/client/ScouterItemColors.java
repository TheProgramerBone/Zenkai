package com.hmc.zenkai.client;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.content.item.ModItems;
import com.hmc.zenkai.content.item.special.ScouterItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.DyedItemColor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

/**
 * Tinte del ICONO del scouter en inventario/mano: el modelo de item tiene dos capas
 * (layer0 = armazón, tal cual; layer1 = cristal en ESCALA DE GRISES) y aquí se tiñe la layer1
 * con el color del tinte vanilla del stack (o el verde por defecto si no está teñido).
 *
 * Evento de MOD BUS (RegisterColorHandlersEvent.Item), solo cliente.
 * Nota 1.21: el color devuelto es ARGB (el alpha FF es obligatorio o la capa sale invisible).
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public final class ScouterItemColors {
    private ScouterItemColors() {}

    @SubscribeEvent
    public static void onRegisterItemColors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tintIndex) -> {
            if (tintIndex != 1) return 0xFFFFFFFF;          // layer0 (armazón): sin teñir
            int rgb = stack.has(DataComponents.DYED_COLOR)
                    ? DyedItemColor.getOrDefault(stack, ScouterItem.DEFAULT_TINT)
                    : ScouterItem.DEFAULT_TINT;
            return 0xFF000000 | (rgb & 0xFFFFFF);           // layer1 (cristal): color del tinte
        }, ModItems.SCOUTER.get());
    }
}