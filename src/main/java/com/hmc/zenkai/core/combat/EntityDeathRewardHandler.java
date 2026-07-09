package com.hmc.zenkai.core.combat;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.core.network.feature.player.PlayerLifeCycle;
import com.hmc.zenkai.core.network.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.core.network.feature.stats.DataAttachments;
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
        if (!dead.hasData(DataAttachments.ENTITY_STATS.get())) return;

        EntityStats stats = dead.getData(DataAttachments.ENTITY_STATS.get());
        if (!stats.isInitialized() || stats.getTpReward() <= 0) return;

        Player killer = resolveKiller(event.getSource());
        if (killer == null) return;

        PlayerStatsAttachment ka = PlayerStatsAttachment.get(killer);
        if (!ka.isRaceChosen()) return;

        ka.addTP(stats.getTpReward());
        PlayerLifeCycle.syncIfServer(killer);
    }

    private static Player resolveKiller(DamageSource src) {
        if (src.getEntity() instanceof Player p) return p;
        if (src.getDirectEntity() instanceof Projectile proj && proj.getOwner() instanceof Player p) return p;
        return null;
    }
}