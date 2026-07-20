package com.hmc.zenkai.worldgen;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.core.ModGameRules;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;

/**
 * Cancela el spawn NATURAL de mobs HOSTILES (Enemy) dentro de las zonas protegidas
 * (MobSpawnEvent.PositionCheck -> FAIL). No afecta a mobs colocados por ti.
 * Respeta la gamerule zenkai_enableStructureProtection.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID)
public final class NoHostileSpawnHandler {
    private NoHostileSpawnHandler() {}

    @SubscribeEvent
    public static void onPositionCheck(MobSpawnEvent.PositionCheck event) {
        if (!(event.getEntity() instanceof Enemy)) return;

        ServerLevel level = event.getLevel().getLevel();
        if (!ModGameRules.enableStructureProtection(level.getServer())) return;

        ResourceKey<Level> dim = level.dimension();
        if (NoHostileSpawnZones.isProtected(dim, event.getX(), event.getY(), event.getZ())) {
            event.setResult(MobSpawnEvent.PositionCheck.Result.FAIL);
        }
    }
}