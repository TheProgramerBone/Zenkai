package com.hmc.zenkai.core.network.feature.aura;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.core.config.StatsConfig;
import com.hmc.zenkai.core.network.feature.player.PlayerLifeCycle;
import com.hmc.zenkai.core.network.feature.player.PlayerStatsAttachment;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Turbo server-autoritativo: válida el toggle, drena energía (aura.turbo_drain_pct_per_sec
 * de energyMax, cobrado 1 vez por segundo), autoapaga sin energía, y sincroniza a trackers
 * + al propio jugador (patrón FlyAnimServerState). Muere/desloguea = off.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID)
public final class TurboServerState {
    private TurboServerState() {}

    private static final Set<UUID> ON = new HashSet<>();

    public static boolean isOn(ServerPlayer sp) { return ON.contains(sp.getUUID()); }

    public static void set(ServerPlayer sp, boolean on) {
        PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
        if (on) {
            if (!att.isRaceChosen() || att.getEnergy() < drainPerSecond(att)) {
                broadcast(sp, false); // rechazado: fuerza el estado real en el cliente
                return;
            }
            ON.add(sp.getUUID());
        } else {
            ON.remove(sp.getUUID());
        }
        broadcast(sp, on);
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        if (!ON.contains(sp.getUUID())) return;
        if (sp.level().getGameTime() % 20 != 0) return; // cobro 1 vez por segundo

        PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
        int cost = drainPerSecond(att);
        if (att.getEnergy() < cost) {
            set(sp, false); // sin energía: auto-apagado (el cliente lo ve por el sync)
            return;
        }
        att.addEnergy(-cost);
        PlayerLifeCycle.syncIfServer(sp);
    }

    /** Al empezar a trackear a alguien en turbo, mandarle el estado actual. */
    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking e) {
        if (e.getTarget() instanceof ServerPlayer target
                && ON.contains(target.getUUID())
                && e.getEntity() instanceof ServerPlayer viewer) {
            PacketDistributor.sendToPlayer(viewer, new TurboSyncPacket(target.getId(), true));
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp && ON.contains(sp.getUUID())) {
            set(sp, false);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        Player p = e.getEntity();
        ON.remove(p.getUUID());
    }

    private static int drainPerSecond(PlayerStatsAttachment att) {
        return Math.max(1, (int) Math.ceil(att.getEnergyMax() * StatsConfig.turboDrainPctPerSec()));
    }

    private static void broadcast(ServerPlayer sp, boolean on) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(sp, new TurboSyncPacket(sp.getId(), on));
    }
}