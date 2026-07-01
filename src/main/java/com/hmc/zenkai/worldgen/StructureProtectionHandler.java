package com.hmc.zenkai.worldgen;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.core.ModGameRules;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * Dentro de las zonas protegidas no se pueden romper ni colocar bloques.
 * Los jugadores en creativo sí pueden (para editar/mantener). Respeta la gamerule
 * zenkai_enableStructureProtection.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID)
public final class StructureProtectionHandler {
    private StructureProtectionHandler() {}

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        Player player = event.getPlayer();
        if (player == null || player.isCreative()) return;
        if (protectedAt(player, event.getPos())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.isCreative()) return;
        if (protectedAt(player, event.getPos())) event.setCanceled(true);
    }

    private static boolean protectedAt(Player player, BlockPos pos) {
        Level level = player.level();
        if (level.isClientSide) return false;
        if (level.getServer() != null && ModGameRules.enableStructureProtection(level.getServer())) return false;
        return NoHostileSpawnZones.isProtected(level.dimension(), pos.getX(), pos.getY(), pos.getZ());
    }
}