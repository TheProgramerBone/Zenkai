package com.hmc.zenkai.feature.teleport;

import com.hmc.zenkai.Zenkai;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * Manda InstantTransmissionMenuStatePacket (descubrimiento + dimensiones visitadas) al login,
 * mismo patrón que PlayerLifeCycle.onLogin con los attachments de stats/visual — sin esto la
 * pantalla del menú vería siempre los dos conjuntos vacíos tras reconectar. La resincronización
 * tras un descubrimiento NUEVO en pleno juego la dispara directamente
 * TeleportDiscoverySystem llamando a {@link #send}.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID)
public final class InstantTransmissionMenuSync {
    private InstantTransmissionMenuSync() {}

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) send(sp);
    }

    public static void send(ServerPlayer sp) {
        InstantTransmissionAttachment att = InstantTransmissionAttachment.get(sp);
        PacketDistributor.sendToPlayer(sp, new InstantTransmissionMenuStatePacket(
                List.copyOf(att.discoveredIdsView()), List.copyOf(att.visitedDimensionIdsView())));
    }
}
