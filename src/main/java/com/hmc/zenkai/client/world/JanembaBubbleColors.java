package com.hmc.zenkai.client.world;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.content.block.JanembaBubbleBlock;
import com.hmc.zenkai.registry.ModBlocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

/**
 * Tinte de la gelatina de las esferas del Janemba Rift: la textura es gris y el modelo lleva
 * tintindex 0 en las 6 caras, así que el color del estado ({@link JanembaBubbleBlock#tint}) es el
 * color que se ve. El ítem lee su color del componente BLOCK_STATE (rosa si no tiene).
 *
 * Eventos de MOD BUS, solo cliente (mismo patrón que ScouterItemColors). Color ARGB: sin el alpha
 * FF el ítem sale invisible.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public final class JanembaBubbleColors {
    private JanembaBubbleColors() {}

    @SubscribeEvent
    public static void onRegisterBlockColors(RegisterColorHandlersEvent.Block event) {
        event.register((state, level, pos, tintIndex) -> 0xFF000000 | JanembaBubbleBlock.tint(state),
                ModBlocks.HFIL_JANEMBA_BUBBLE.get());
    }

    /** El ítem lleva su color en el componente BLOCK_STATE (tintado o recogido con toque de seda). */
    @SubscribeEvent
    public static void onRegisterItemColors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tintIndex) -> 0xFF000000 | JanembaBubbleBlock.tint(JanembaBubbleBlock.colorOf(stack), false),
                ModBlocks.HFIL_JANEMBA_BUBBLE.get());
    }
}
