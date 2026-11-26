package com.hmc.zenkai.client;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.content.item.CapsuleRemoteItem;
import com.hmc.zenkai.feature.capsule.CapsuleRemoteCyclePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Shift + rueda del ratón con el control remoto de cápsulas en la mano: cambia la cápsula seleccionada (decisión del
 * usuario 2026-11-25) en vez de mover la barra rápida. Prioridad alta para ganarle a la rueda del lock-on, que solo actúa
 * con su opción encendida; sin Shift o sin el control, la rueda hace lo de siempre.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public final class CapsuleRemoteClient {
    private CapsuleRemoteClient() {}

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onScroll(InputEvent.MouseScrollingEvent e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null || !mc.player.isShiftKeyDown() || e.getScrollDeltaY() == 0) return;
        if (!(mc.player.getItemInHand(InteractionHand.MAIN_HAND).getItem() instanceof CapsuleRemoteItem)
                && !(mc.player.getItemInHand(InteractionHand.OFF_HAND).getItem() instanceof CapsuleRemoteItem)) return;
        // Rueda hacia arriba = anterior, como la barra rápida de vanilla.
        PacketDistributor.sendToServer(new CapsuleRemoteCyclePayload(e.getScrollDeltaY() > 0 ? -1 : 1));
        e.setCanceled(true);
    }
}
