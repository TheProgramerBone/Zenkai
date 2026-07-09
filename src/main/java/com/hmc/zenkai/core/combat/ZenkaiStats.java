package com.hmc.zenkai.core.combat;

import com.hmc.zenkai.core.network.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.core.network.feature.stats.DataAttachments;
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
}