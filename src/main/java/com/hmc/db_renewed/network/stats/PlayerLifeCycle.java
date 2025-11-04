package com.hmc.db_renewed.network.stats;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public class PlayerLifeCycle {

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        var newAtt = e.getEntity().getData(DataAttachments.PLAYER_STATS.get());
        var oldAtt = e.getOriginal().getData(DataAttachments.PLAYER_STATS.get());
        if (newAtt != null && oldAtt != null) {
            newAtt.load(oldAtt.save());
            sync(sp);
        }
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