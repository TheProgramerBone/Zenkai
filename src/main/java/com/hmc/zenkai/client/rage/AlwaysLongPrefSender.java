package com.hmc.zenkai.client.rage;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.config.ClientConfig;
import com.hmc.zenkai.feature.rage.AlwaysLongPrefPacket;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Mantiene al servidor al tanto de la opción «siempre la secuencia larga». Se reenvía al entrar a un mundo o servidor (el servidor no la
 * guarda) y cada vez que cambia, venga de la placa del árbol o de la pantalla de Config: se compara una vez por segundo en vez de
 * enganchar cada vía de edición.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public final class AlwaysLongPrefSender {
    private AlwaysLongPrefSender() {}

    /** Último valor enviado en esta conexión; null = aún no se ha mandado nada. */
    private static Boolean lastSent;

    @SubscribeEvent
    public static void onLogin(ClientPlayerNetworkEvent.LoggingIn e) { lastSent = null; }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null || mc.player.tickCount % 20 != 5) return;
        boolean now = ClientConfig.alwaysLongTransform();
        if (lastSent != null && lastSent == now) return;
        // Apagada y sin nada enviado: el servidor ya parte de «apagada», no hace falta un paquete.
        if (lastSent == null && !now) { lastSent = false; return; }
        lastSent = now;
        PacketDistributor.sendToServer(new AlwaysLongPrefPacket(now));
    }
}
