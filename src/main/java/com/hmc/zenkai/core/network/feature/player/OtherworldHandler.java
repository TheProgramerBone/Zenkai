package com.hmc.zenkai.core.network.feature.player;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.core.ModGameRules;
import com.hmc.zenkai.worldgen.NoHostileSpawnZones;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
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

        // Muerte OCURRIDA DENTRO del otro mundo (p. ej. /kill, PvP de entrenamiento):
        // se mantiene el comportamiento suave -> se cancela y se re-ancla ahí mismo,
        // sin reiniciar el temporizador de Yemma. (No hay pantalla aquí: es el campo de
        // entrenamiento; si quieres pantalla también aquí, dilo y lo cambio.)
        if (OtherworldManager.isInOtherworld(player)) {
            event.setCanceled(true);
            OtherworldManager.keepInOtherworld(player);
            return;
        }

        if (!ModGameRules.enableOtherworld(player.server)) return;   // muerte normal (pantalla + respawn vanilla)

        // Otro mundo ACTIVO: NO cancelamos la muerte -> sale la pantalla de muerte "sí o sí".
        // Solo marcamos el destino; el teletransporte real ocurre en el respawn (onPlayerRespawn).
        OtherworldManager.markPendingOtherworld(player);
    }

    /**
     * Tras pulsar "Reaparecer" en la pantalla de muerte: si el jugador quedó marcado para el
     * otro mundo, lo redirigimos allí (en vez de a su cama/spawn). Si no, respawn vanilla normal.
     */
    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (event.isEndConquered()) return;                          // salir del End: no aplica
        if (!OtherworldManager.isInOtherworld(player)) return;

        OtherworldManager.respawnIntoOtherworld(player);
    }

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!OtherworldManager.isInOtherworld(player)) return;
        if (player.isCreative()) return; // en creativo sí puede
        // Dentro de una zona protegida (el palacio) SÍ se puede construir: lo gestiona
        // StructureProtectionHandler (registrar + romper solo lo propio). Fuera del palacio,
        // en el más allá no se construye.
        BlockPos p = event.getPos();
        if (NoHostileSpawnZones.isProtected(player.level().dimension(), p.getX(), p.getY(), p.getZ())) return;
        event.setCanceled(true);
    }
}