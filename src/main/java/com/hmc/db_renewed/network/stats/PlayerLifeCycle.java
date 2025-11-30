package com.hmc.db_renewed.network.stats;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public class PlayerLifeCycle {

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent e) {
        Player p = e.getEntity();
        if (p.level().isClientSide()) return; // solo servidor

        PlayerStatsAttachment att = p.getData(DataAttachments.PLAYER_STATS.get());

        // Rellenar pools al respawn
        att.refillOnRespawn();

        // Sincronizar al cliente
        syncIfServer(p);
    }

    @SubscribeEvent
    public static void onLogin(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) sync(sp);
    }

    @SubscribeEvent
    public static void onDimChange(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) sync(sp);
    }

    public static void sync(ServerPlayer sp) {
        PlayerStatsAttachment att = sp.getData(DataAttachments.PLAYER_STATS.get());
        PacketDistributor.sendToPlayer(sp, SyncPlayerStatsPacket.from(att));
    }

    public static void syncIfServer(net.minecraft.world.entity.player.Player p) {
        if (p instanceof ServerPlayer sp) sync(sp);
    }
}