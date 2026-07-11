package com.hmc.zenkai.core.network.feature.ki;

import com.hmc.zenkai.Zenkai;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lado SERVIDOR de la sincronización de animaciones de vuelo.
 *
 *  - update(): guarda el último estado (para nuevos trackers) y lo re-difunde a los
 *    jugadores que trackean al que vuela.
 *  - StartTracking: quien empieza a ver a un jugador que YA está volando recibe su estado
 *    al instante (sin esto, no vería la animación hasta el siguiente cambio).
 *  - Respawn / logout: limpia y avisa "no volando" (evita animaciones fantasma tras morir
 *    en pleno vuelo).
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID)
public final class FlyAnimServerState {
    private FlyAnimServerState() {}

    private record State(boolean flying, byte dir, boolean boosting) {}

    private static final Map<UUID, State> STATES = new ConcurrentHashMap<>();

    /** Desde FlyAnimPacket: guarda y difunde a los trackers. */
    public static void update(ServerPlayer sp, boolean flying, byte dir, boolean boosting) {
        if (flying) {
            STATES.put(sp.getUUID(), new State(true, dir, boosting));
        } else {
            STATES.remove(sp.getUUID());
        }
        PacketDistributor.sendToPlayersTrackingEntity(sp,
                new FlyAnimSyncPacket(sp.getId(), flying, dir, boosting));
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking e) {
        if (!(e.getTarget() instanceof ServerPlayer target)) return;
        if (!(e.getEntity() instanceof ServerPlayer tracker)) return;
        State st = STATES.get(target.getUUID());
        if (st != null && st.flying()) {
            PacketDistributor.sendToPlayer(tracker,
                    new FlyAnimSyncPacket(target.getId(), true, st.dir(), st.boosting()));
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
        if (STATES.remove(sp.getUUID()) != null) {
            PacketDistributor.sendToPlayersTrackingEntity(sp,
                    new FlyAnimSyncPacket(sp.getId(), false, (byte) 0, false));
        }
    }
}