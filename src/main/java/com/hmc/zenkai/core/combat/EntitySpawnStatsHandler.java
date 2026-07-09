package com.hmc.zenkai.core.combat;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.core.network.feature.stats.DataAttachments;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/**
 * Al entrar una entidad al mundo (servidor), si su tipo tiene un plano en zenkai_entities/*.json
 * y aún no está poblada, resuelve sus stats (PL+arquetipo+overrides -> atributos+pools).
 *
 * Si la entidad se cargó del disco, su EntityStats ya viene serializado (initialized=true) y no
 * se toca -> un jefe herido conserva su body.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID)
public final class EntitySpawnStatsHandler {
    private EntitySpawnStatsHandler() {}

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof LivingEntity le) || le instanceof Player) return;

        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(le.getType());
        EntityStatDef def = EntityStatsManager.get(id);
        if (def == null || def.displayOnly()) return;

        EntityStats stats = le.getData(DataAttachments.ENTITY_STATS.get());
        if (!stats.isInitialized()) {
            stats.applyDef(def);
            le.setData(DataAttachments.ENTITY_STATS.get(), stats);
        }
    }
}