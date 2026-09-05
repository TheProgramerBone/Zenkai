package com.hmc.zenkai.feature.player;

import com.hmc.zenkai.config.ServerConfig;
import com.hmc.zenkai.registry.ModEffects;
import com.hmc.zenkai.registry.ZenkaiDataAttachments;
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
        PlayerStatsAttachment att = p.getData(ZenkaiDataAttachments.PLAYER_STATS.get());
        att.setImmortal(false);
        p.removeEffect(ModEffects.IMMORTALITY);
        p.removeEffect(ModEffects.MAJIN);
        att.refillOnRespawn();
        var visual = p.getData(ZenkaiDataAttachments.PLAYER_VISUAL.get());
        visual.setMajinControlled(false);
        syncIfServer(p);          // stats
        syncVisualIfServer(p);    // visual
    }

    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        PlayerStatsAttachment att = sp.getData(ZenkaiDataAttachments.PLAYER_STATS.get());
        att.setImmortal(false);
        sp.removeEffect(ModEffects.IMMORTALITY);
        sp.getData(ZenkaiDataAttachments.PLAYER_VISUAL.get()).setMajinControlled(false);
        sp.getData(ZenkaiDataAttachments.PLAYER_FORM.get()).clearStrain();
        sp.removeEffect(ModEffects.MAJIN);
        att.skills().clearToggles();
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
            PacketDistributor.sendToPlayer(tracker, SyncPlayerStatsPacket.from(targetPlayer));
            PacketDistributor.sendToPlayer(tracker, SyncPlayerVisualPacket.from(targetPlayer));
        }
    }


    public static void sync(ServerPlayer sp) {
        mirrorHealth(sp);
        var pkt = SyncPlayerStatsPacket.from(sp);
        PacketDistributor.sendToPlayer(sp, pkt);
        PacketDistributor.sendToPlayersTrackingEntity(sp, pkt);
    }

    /**
     * ESPEJO BODY -> CORAZONES. Va DENTRO de sync() y no en cada sitio que toca el body, que
     * es lo mismo que hace ZenkaiCombatStats.mirrorToVanilla con las entidades: cualquier ruta que
     * cambia el pool ya termina llamando aquí, así que el espejo no se puede olvidar.
     * Esto SUSTITUYE a los setHealth(getMaxHealth()) sueltos repartidos por DownedDeathGuard,
     * SenzuBean, OtherworldManager, CombatZenkaiHooks e ImmortalityEffect: si sobrevive alguno,
     * hay dos escritores de la vida y vuelven a descuadrarse.
     * NUNCA baja de 1.0F (medio corazón). Poner 0 mata al jugador y se salta el derribado
     * entero; quien mata de verdad es DownedSystem, deliberadamente y en un solo sitio.
     * Sin raza no se toca nada: sin sistema zenkai la vida vanilla es suya.
     */
    private static void mirrorHealth(ServerPlayer sp) {
        if (!ServerConfig.mirrorHealth()) return;
        // Nunca se resucita a un muerto: subirle la vida rompe el respawn (ver arriba).
        // isDeadOrDying cubre vida <= 0 y el flag dead de una muerte ya consumada.
        if (sp.isDeadOrDying()) return;

        PlayerStatsAttachment att = sp.getData(ZenkaiDataAttachments.PLAYER_STATS.get());
        if (!att.isRaceChosen()) return;

        int max = att.getBodyMax();
        if (max <= 0) return;

        float target = sp.getMaxHealth() * (att.getBody() / (float) max);
        sp.setHealth(Math.max(1.0F, Math.min(sp.getMaxHealth(), target)));
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
     * - y el resto de quienes lo están viendo también
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

    public static void syncForm(ServerPlayer sp) {
        var pkt = SyncPlayerFormPacket.from(sp);
        PacketDistributor.sendToPlayer(sp, pkt);
        PacketDistributor.sendToPlayersTrackingEntity(sp, pkt);
    }

    public static void syncFormIfServer(Player p) {
        if (p instanceof ServerPlayer sp) syncForm(sp);
    }

    public static void syncFormToTrackersAndSelf(ServerPlayer target) {
        var pkt = SyncPlayerFormPacket.from(target);
        PacketDistributor.sendToPlayer(target, pkt);
        PacketDistributor.sendToPlayersTrackingEntity(target, pkt);
    }
}