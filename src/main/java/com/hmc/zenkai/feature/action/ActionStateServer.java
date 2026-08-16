package com.hmc.zenkai.feature.action;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Almacén AUTORITATIVO de la acción exclusiva. Lo escribe SOLO ActionResolver.
 *
 * En el paso 1 esto derivaba el estado de los almacenes existentes cada tick. Ya no: la
 * dependencia está invertida y el derive() desapareció. Lo que queda del tick es únicamente
 * la red de seguridad de derribo/muerte, que son gates globales y pueden llegar por vías que
 * no pasan por el resolver (daño, caída, comando).
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID)
public final class ActionStateServer {

    private ActionStateServer() {}

    private static final Map<UUID, ActionState> STATES = new ConcurrentHashMap<>();

    public static ActionState get(ServerPlayer sp) {
        return STATES.getOrDefault(sp.getUUID(), ActionState.NONE);
    }

    public static void set(ServerPlayer sp, ActionState st) {
        ActionState old = STATES.put(sp.getUUID(), st);
        if (!sameShape(old, st)) broadcast(sp, st);
    }

    public static void clear(ServerPlayer sp) {
        ActionState old = STATES.remove(sp.getUUID());
        if (old != null && !old.isNone()) broadcast(sp, ActionState.NONE);
    }

    /** Limpia solo si lo que hay es del tipo esperado. Lo usan los ejecutores al terminar,
     *  para no borrar una acción que ya los sustituyó dentro del mismo tick. */
    public static void clearIf(ServerPlayer sp, ActionType expected) {
        ActionState cur = get(sp);
        if (cur.type() == expected) clear(sp);
    }

    private static boolean sameShape(ActionState a, ActionState b) {
        return a != null && b != null && a.type() == b.type() && a.phase() == b.phase()
                && a.payload() == b.payload() && a.startTick() == b.startTick();
    }

    private static void broadcast(ServerPlayer sp, ActionState st) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(sp,
                ActionStateSyncPacket.of(sp.getId(), st));
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        if (STATES.get(sp.getUUID()) == null) return;
        PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
        if (att.flags().isDowned()) clear(sp);
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) clear(sp);
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking e) {
        if (e.getTarget() instanceof ServerPlayer target
                && e.getEntity() instanceof ServerPlayer viewer) {
            ActionState st = STATES.get(target.getUUID());
            if (st != null && !st.isNone()) {
                PacketDistributor.sendToPlayer(viewer,
                        ActionStateSyncPacket.of(target.getId(), st));
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        STATES.remove(e.getEntity().getUUID());
    }
}