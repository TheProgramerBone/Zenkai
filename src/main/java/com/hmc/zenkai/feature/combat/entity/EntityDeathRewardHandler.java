package com.hmc.zenkai.feature.combat.entity;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.config.ServerConfig;
import com.hmc.zenkai.content.entity.misc.ShadowCloneEntity;
import com.hmc.zenkai.feature.player.PlayerLifeCycle;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.feature.training.TrainingHooks;
import com.hmc.zenkai.registry.ZenkaiDataAttachments;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/**
 * Recompensa de TP al matar una entidad con stats. Escala por el PL de la entidad (o número fijo
 * del JSON). El "mundo TP" real (minijuegos/historia) va aparte; esto es el gancho base.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID)
public final class EntityDeathRewardHandler {
    private EntityDeathRewardHandler() {}

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        if (event.getEntity() instanceof Player) return;
        LivingEntity dead = event.getEntity();

        Player killer = resolveKiller(event.getSource());
        if (killer == null) return;

        PlayerStatsAttachment ka = PlayerStatsAttachment.get(killer);
        if (!ka.isRaceChosen()) return;

        int reward;
        if (dead.hasData(ZenkaiDataAttachments.ENTITY_STATS.get())
                && dead.getData(ZenkaiDataAttachments.ENTITY_STATS.get()).isInitialized()) {
            reward = dead.getData(ZenkaiDataAttachments.ENTITY_STATS.get()).getTpReward();
        } else {
            // Mob vanilla (sin stats): PL derivado de su vida máxima. Mínimo 1 TP.
            double vanillaPl = dead.getMaxHealth() * ServerConfig.vanillaPowerLevelFactor();
            reward = (int) Math.max(1, Math.round(vanillaPl * ServerConfig.tpPerPl()));
        }
        if (reward <= 0) return;

        // Ruta ÚNICA. Antes había un ka.addTP(reward) debajo de esta llamada que concedía el
        // reward por segunda vez Y se saltaba el embudo entero, así que esa mitad no acumulaba
        // fatiga y no decaía nunca. El comentario ya decía "ruta única"; la segunda ruta era un
        // resto de antes de que existiera grantFromKill.
        if (killer instanceof ServerPlayer sp) {
            int granted = TrainingHooks.grantFromKill(sp, reward, victimPowerLevel(dead));

            // "Train with your shadow": aviso explícito de cuánto TP dejó el clon al caer,
            // pedido por el usuario — sin esto el jugador solo veía subir el número de TP en
            // el HUD, sin saber a qué golpe atribuirlo. Solo esta entidad concreta lo dispara;
            // el resto de kills (mobs normales) no ganan un mensaje nuevo, mismo criterio que
            // ya evita ruido de chat por cada zombi.
            if (dead instanceof ShadowCloneEntity && granted > 0) {
                sp.displayClientMessage(
                        Component.translatable("messages.zenkai.shadow_defeated", granted), true);
            }
        }
    }

    /** PL de la víctima para el factor de diferencia. Sin stats zenkai se deriva de la vida
     *  máxima, igual que el reward de arriba. */
    private static long victimPowerLevel(LivingEntity dead) {
        var st = dead.getData(ZenkaiDataAttachments.ENTITY_STATS.get());
        if (dead.hasData(ZenkaiDataAttachments.ENTITY_STATS.get()) && st.isInitialized()) {
            return st.getPowerLevel();
        }
        return Math.max(1L, Math.round(dead.getMaxHealth() * ServerConfig.vanillaPowerLevelFactor()));
    }

    private static Player resolveKiller(DamageSource src) {
        if (src.getEntity() instanceof Player p) return p;
        if (src.getDirectEntity() instanceof Projectile proj && proj.getOwner() instanceof Player p) return p;
        return null;
    }
}