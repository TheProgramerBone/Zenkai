package com.hmc.zenkai.core.network.feature.player;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.core.ModGameRules;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * Sistema de "otro mundo":
 *  - Al morir (si el sistema está activo / hardcore), en vez de morir el jugador
 *    es enviado al otro mundo (se cancela la muerte).
 *  - Mientras está en el otro mundo en SURVIVAL no puede colocar bloques
 *    (en creativo sí). Puede caminar e interactuar normalmente.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID)
public final class OtherworldHandler {
    private OtherworldHandler() {}

    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.level().isClientSide) return;
        if (!ModGameRules.enableOtherworld(player.server)) return;   // hardcore lo fuerza
        if (OtherworldManager.isInOtherworld(player)) return;        // ya estaba allí

        // Cancelar la muerte y enviarlo al otro mundo en su lugar.
        event.setCanceled(true);
        OtherworldManager.sendToOtherworld(player);
    }

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!OtherworldManager.isInOtherworld(player)) return;
        if (player.isCreative()) return; // en creativo sí puede
        event.setCanceled(true);
    }
}