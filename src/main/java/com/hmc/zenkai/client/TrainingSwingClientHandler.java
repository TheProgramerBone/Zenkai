package com.hmc.zenkai.client;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.core.network.feature.training.TrainingSwingPacket;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Golpes al aire (entrenamiento): LeftClickEmpty es un evento SOLO-CLIENTE (el servidor no se
 * entera de los clics al vacío), por eso hace falta el packet. El servidor revalida
 * (mano vacía, cooldown, stamina): esto es solo el aviso.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public final class TrainingSwingClientHandler {
    private TrainingSwingClientHandler() {}

    @SubscribeEvent
    public static void onLeftClickEmpty(PlayerInteractEvent.LeftClickEmpty e) {
        if (!e.getItemStack().isEmpty()) return; // solo puños
        if (!CombatModeClientState.isActive()) return; // entrenar solo en modo combate
        PacketDistributor.sendToServer(new TrainingSwingPacket());
    }
}