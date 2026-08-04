package com.hmc.zenkai.feature.combat.entity;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.config.CommonConfig;
import com.hmc.zenkai.registry.ZenkaiDataAttachments;
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
        EntityStats stats = le.getData(ZenkaiDataAttachments.ENTITY_STATS.get());
        if (stats.isInitialized()) return;

        if (def != null && !def.displayOnly()) {
            stats.applyDef(def, le);
        } else if (def == null && CommonConfig.vanillaStatsFallback()) {
            // Sin JSON: se derivan de sus atributos vanilla. Los display_only se quedan
            // fuera a propósito (PL para el scouter, sin stats de combate).
            stats.applyVanilla(le);
        } else {
            return;
        }
        le.setData(ZenkaiDataAttachments.ENTITY_STATS.get(), stats);
    }
}