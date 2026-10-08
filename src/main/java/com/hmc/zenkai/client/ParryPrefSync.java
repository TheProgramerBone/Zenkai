package com.hmc.zenkai.client;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.config.ClientConfig;
import com.hmc.zenkai.feature.combat.ParryPrefPacket;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Mantiene al servidor al día con la preferencia de parry del jugador (ClientConfig parry.trigger):
 * la manda al entrar y, después, solo cuando cambia (se compara cada tick, es barato y no depende
 * de engancharse al guardado de la config).
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public final class ParryPrefSync {
    private ParryPrefSync() {}

    /** Último valor enviado; null = hay que (re)enviarlo, p. ej. recién conectado. */
    private static Boolean lastSent = null;

    @SubscribeEvent
    public static void onLogin(ClientPlayerNetworkEvent.LoggingIn e) { lastSent = null; }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) return;
        boolean now = ClientConfig.parryTrigger().allowsBlock();
        if (lastSent != null && lastSent == now) return;
        lastSent = now;
        PacketDistributor.sendToServer(new ParryPrefPacket(now));
    }
}
