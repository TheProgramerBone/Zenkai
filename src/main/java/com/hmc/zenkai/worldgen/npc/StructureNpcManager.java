package com.hmc.zenkai.worldgen.npc;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.core.ModGameRules;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Mantiene presentes a todos los NPCs de {@link StructureNpcs}. Si el gamerule
 * zenkai_keepStructureNpcs está activo, re-spawnea cualquiera que falte (p. ej.
 * si muere). Añadir NPCs no requiere tocar esta clase: solo StructureNpcs.ALL.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID)
public final class StructureNpcManager {
    private StructureNpcManager() {}

    /** Cada cuántos ticks se comprueba (100 = 5 s). */
    private static final int CHECK_INTERVAL = 100;

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % CHECK_INTERVAL != 0) return;
        if (!ModGameRules.keepStructureNpcs(server)) return;

        for (StructureNpc npc : StructureNpcs.ALL) {
            ServerLevel level = server.getLevel(npc.dimension());
            if (level == null) continue;                 // dimensión no cargada
            if (!level.hasChunkAt(npc.pos())) continue;  // zona no cargada (nadie cerca)
            ensure(level, npc);
        }
    }

    /** Asegura los NPCs de UNA dimensión (para llamar al teletransportar allí). */
    public static void ensureAllIn(ServerLevel level) {
        if (!ModGameRules.keepStructureNpcs(level.getServer())) return;
        for (StructureNpc npc : StructureNpcs.ALL) {
            if (npc.dimension() != level.dimension()) continue;
            ensure(level, npc);
        }
    }

    /** Spawnea el NPC si no hay ya uno de su tipo cerca (idempotente). */
    private static void ensure(ServerLevel level, StructureNpc npc) {
        EntityType<?> type = npc.type().get();
        AABB area = new AABB(npc.pos()).inflate(npc.radius());
        boolean exists = !level.getEntitiesOfClass(Mob.class, area, m -> m.getType() == type).isEmpty();
        if (exists) return;

        Entity created = type.create(level);
        if (!(created instanceof Mob mob)) return;

        mob.moveTo(npc.pos().getX() + 0.5, npc.pos().getY(), npc.pos().getZ() + 0.5, npc.yaw(), 0.0f);
        mob.setYBodyRot(npc.yaw());
        mob.setYHeadRot(npc.yaw());
        mob.setPersistenceRequired();
        level.addFreshEntity(mob);
    }
}