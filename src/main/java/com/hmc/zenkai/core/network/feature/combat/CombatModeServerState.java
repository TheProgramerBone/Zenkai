package com.hmc.zenkai.core.network.feature.combat;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.core.network.feature.player.PlayerStatsAttachment;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lado SERVIDOR del modo combate (mismo patrón que FlyAnimServerState):
 * guarda el último estado por jugador, lo difunde a los trackers, se lo manda a quien
 * empieza a trackear, y limpia (avisando) en respawn/logout.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID)
public final class CombatModeServerState {
    private CombatModeServerState() {}

    private static final Map<UUID, Byte> ACTIVE = new ConcurrentHashMap<>(); // valor = estilo

    public static void update(ServerPlayer sp, boolean active) {
        byte style = (byte) PlayerStatsAttachment.get(sp).getStyle().ordinal();
        if (active) {
            ACTIVE.put(sp.getUUID(), style);
        } else {
            ACTIVE.remove(sp.getUUID());
        }
        PacketDistributor.sendToPlayersTrackingEntity(sp,
                new CombatModeSyncPacket(sp.getId(), active, style));
    }

    /** ¿Está este jugador en modo combate? (consulta del pipeline de daño). */
    public static boolean isActive(java.util.UUID playerId) {
        return ACTIVE.containsKey(playerId);
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking e) {
        if (!(e.getTarget() instanceof ServerPlayer target)) return;
        if (!(e.getEntity() instanceof ServerPlayer tracker)) return;
        Byte style = ACTIVE.get(target.getUUID());
        if (style != null) {
            PacketDistributor.sendToPlayer(tracker,
                    new CombatModeSyncPacket(target.getId(), true, style));
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) clear(sp);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) clear(sp);
    }

    private static void clear(ServerPlayer sp) {
        if (ACTIVE.remove(sp.getUUID()) != null) {
            PacketDistributor.sendToPlayersTrackingEntity(sp,
                    new CombatModeSyncPacket(sp.getId(), false, (byte) 0));
        }
    }
}