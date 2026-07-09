package com.hmc.zenkai.core.combat;

import com.hmc.zenkai.core.config.StatsConfig;
import com.hmc.zenkai.core.network.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.core.network.feature.stats.DataAttachments;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Puente: dada cualquier entidad, devuelve su {@link ZenkaiCombatStats} (jugador o entidad con
 * stats), o null si no participa. El pipeline de combate solo habla con la interfaz.
 */
public final class ZenkaiStats {
    private ZenkaiStats() {}

    public static ZenkaiCombatStats of(Entity e) {
        if (e instanceof Player p) {
            return PlayerStatsAttachment.get(p);
        }
        if (e instanceof LivingEntity le && le.hasData(DataAttachments.ENTITY_STATS.get())) {
            EntityStats s = le.getData(DataAttachments.ENTITY_STATS.get());
            return s.isInitialized() ? s : null;
        }
        return null;
    }

    /** Los stats de entidad (no jugador) ya resueltos, o null. */
    public static EntityStats entityStats(LivingEntity le) {
        if (le instanceof Player || !le.hasData(DataAttachments.ENTITY_STATS.get())) return null;
        EntityStats s = le.getData(DataAttachments.ENTITY_STATS.get());
        return s.isInitialized() ? s : null;
    }

    /**
     * PL "de display" de CUALQUIER entidad viva (scouter / sentir ki), por prioridad:
     *  1. Jugador con raza / entidad con stats -> su PL real.
     *  2. Entidad con JSON display_only -> el PL fijo del JSON.
     *  3. Fallback vanilla -> vida_max x factor (config power_level.vanilla_factor).
     */
    public static long resolveDisplayPowerLevel(LivingEntity le) {
        ZenkaiCombatStats stats = of(le);
        if (stats != null && stats.isCombatActive()) {
            return stats.getPowerLevel();
        }
        if (!(le instanceof Player)) {
            ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(le.getType());
            EntityStatDef def = EntityStatsManager.get(id);
            if (def != null && def.displayOnly()) return def.powerLevel();
        }
        return Math.round(le.getMaxHealth() * StatsConfig.vanillaPowerLevelFactor());
    }
}