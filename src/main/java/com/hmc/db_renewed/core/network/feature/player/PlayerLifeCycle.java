package com.hmc.db_renewed.core.network.feature.player;

import com.hmc.db_renewed.content.effect.ModEffects;
import com.hmc.db_renewed.core.network.feature.stats.DataAttachments;
import com.hmc.db_renewed.core.network.feature.stats.SyncPlayerStatsPacket;
import com.hmc.db_renewed.core.network.feature.stats.SyncPlayerVisualPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public class PlayerLifeCycle {

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent e) {
        Player p = e.getEntity();
        if (p.level().isClientSide()) return;
        PlayerStatsAttachment att = p.getData(DataAttachments.PLAYER_STATS.get());
        att.setImmortal(false);
        p.removeEffect(ModEffects.IMMORTALITY);
        att.refillOnRespawn();

        syncIfServer(p);          // stats
        syncVisualIfServer(p);    // visual
    }

    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;

        PlayerStatsAttachment att = sp.getData(DataAttachments.PLAYER_STATS.get());

        // Al morir, cortamos de raíz cualquier rastro de inmortalidad
        att.setImmortal(false);
        sp.removeEffect(ModEffects.IMMORTALITY);

        // Opcional: si quieres, también puedes resetear body/stats aquí,
        // pero normalmente eso ya lo haces en el respawn con refillOnRespawn().
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) {
            sync(sp);
            syncVisual(sp);
        }
    }

    @SubscribeEvent
    public static void onDimChange(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) {
            sync(sp);
            syncVisual(sp);
        }
    }


    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking e) {
        if (!(e.getEntity() instanceof ServerPlayer tracker)) return;

        Entity target = e.getTarget();
        if (target instanceof ServerPlayer targetPlayer) {
            // tracker ahora puede verlo -> mandarle su data
            PacketDistributor.sendToPlayer(tracker, SyncPlayerStatsPacket.from(targetPlayer.getData(DataAttachments.PLAYER_STATS.get())));
            PacketDistributor.sendToPlayer(tracker, SyncPlayerVisualPacket.from(targetPlayer));
        }
    }


    public static void sync(ServerPlayer sp) {
        PlayerStatsAttachment att = sp.getData(DataAttachments.PLAYER_STATS.get());
        PacketDistributor.sendToPlayer(sp, SyncPlayerStatsPacket.from(att));
        PacketDistributor.sendToPlayersTrackingEntity(sp, SyncPlayerStatsPacket.from(att));
    }

    public static void syncIfServer(Player p) {
        if (p instanceof ServerPlayer sp) sync(sp);
    }

    // ==========================
    // Sync Visual
    // ==========================
    public static void syncVisual(ServerPlayer sp) {
        PacketDistributor.sendToPlayer(sp, SyncPlayerVisualPacket.from(sp));
    }

    public static void syncVisualIfServer(Player p) {
        if (p instanceof ServerPlayer sp) syncVisual(sp);
    }

    /**
     * Útil: cuando cambies raza o raceSkin en servidor, llama esto para que:
     * - el jugador se vea a sí mismo
     * - y todos los que lo están viendo también
     *
     * Si tu NeoForge no tiene este helper exacto en PacketDistributor, te digo abajo qué hacer.
     */
    public static void syncVisualToTrackersAndSelf(ServerPlayer target) {
        var pkt = SyncPlayerVisualPacket.from(target);

        // Opción A (si existe en tu NeoForge):
        // PacketDistributor.sendToPlayersTrackingEntityAndSelf(target, pkt);

        // Opción B (si no existe): manda a self y a tracking en 2 pasos.
        PacketDistributor.sendToPlayer(target, pkt);
        PacketDistributor.sendToPlayersTrackingEntity(target, pkt);
    }
}