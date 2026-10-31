package com.hmc.zenkai.client;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.gui.screens.ShenronPanelScreen;
import com.hmc.zenkai.client.gui.screens.wishes.StackWishScreen;
import com.hmc.zenkai.feature.wishes.WishScreenClosedPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Avisa al servidor cuando el jugador SALE de las pantallas de deseo sin pedir nada, para liberar a Shenlong
 * (un dragón atiende a un jugador a la vez, ver WishSessions). Se vigila por tick en vez de en cada
 * {@code onClose}: así pasar de la pantalla principal a la de un deseo (o al menú de ítems) no cuenta como salir.
 * Cuando SÍ se pide un deseo no hace falta: el servidor cierra la sesión y la pantalla él mismo.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public final class WishScreenWatcher {
    private WishScreenWatcher() {}

    private static boolean inWishScreen;

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        var screen = Minecraft.getInstance().screen;
        boolean now = screen instanceof ShenronPanelScreen || screen instanceof StackWishScreen;
        if (inWishScreen && !now && Minecraft.getInstance().getConnection() != null) {
            PacketDistributor.sendToServer(new WishScreenClosedPayload());
        }
        inWishScreen = now;
    }
}
