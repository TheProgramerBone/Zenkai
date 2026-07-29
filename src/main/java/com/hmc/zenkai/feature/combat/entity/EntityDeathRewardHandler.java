package com.hmc.zenkai.feature.combat.entity;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.config.CommonConfig;
import com.hmc.zenkai.feature.player.PlayerLifeCycle;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.feature.training.TrainingHooks;
import com.hmc.zenkai.registry.ZenkaiDataAttachments;
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

    /** Reward de mobs vanilla: PL = vida máx × vanilla_factor (misma fórmula que el scouter). */
    private static final double VANILLA_TP_PER_PL = 0.05;

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
            double vanillaPl = dead.getMaxHealth() * CommonConfig.vanillaPowerLevelFactor();
            reward = (int) Math.max(1, Math.round(vanillaPl * VANILLA_TP_PER_PL));
        }
        if (reward <= 0) return;

        // Ruta única: el reward pasa por el mismo embudo que el resto del entrenamiento, así
        // fatiga, HTC y pesas se aplican una sola vez y en un solo sitio.
        if (killer instanceof ServerPlayer sp) {
            TrainingHooks.grantFromKill(sp, reward);
        }
        ka.addTP(reward);
        PlayerLifeCycle.syncIfServer(killer);
    }

    private static Player resolveKiller(DamageSource src) {
        if (src.getEntity() instanceof Player p) return p;
        if (src.getDirectEntity() instanceof Projectile proj && proj.getOwner() instanceof Player p) return p;
        return null;
    }
}