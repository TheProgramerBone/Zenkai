package com.hmc.zenkai.worldgen;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.core.ModGameRules;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;

/**
 * Protección de zonas (la HTC entera + las zonas de Kami/Yemma): se PUEDE construir, pero solo se
 * pueden ROMPER los bloques que un jugador haya colocado (registrados en {@link PlayerPlacedBlocks}).
 * La estructura original (colocada por código, no por evento de jugador) no está registrada → queda
 * protegida. Creativo ignora. Respeta la gamerule zenkai_enableStructureProtection.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID)
public final class StructureProtectionHandler {
    private StructureProtectionHandler() {}

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.isCreative()) return;
        Level level = player.level();
        if (level.isClientSide || level.getServer() == null) return;

        BlockPos pos = event.getPos();
        if (isProtectedArea(level, pos)) return;
        PlayerPlacedBlocks.get(level.getServer()).add(level.dimension(), pos);
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        Player player = event.getPlayer();
        if (player.isCreative()) return;
        Level level = player.level();
        if (level.isClientSide || level.getServer() == null) return;

        BlockPos pos = event.getPos();
        if (isProtectedArea(level, pos)) return; // fuera de zona: reglas normales

        PlayerPlacedBlocks data = PlayerPlacedBlocks.get(level.getServer());
        if (data.contains(level.dimension(), pos)) {
            data.remove(level.dimension(), pos);  // era del jugador → se puede romper
        } else {
            event.setCanceled(true);              // bloque original de la estructura → protegido
        }
    }

    private static boolean isProtectedArea(Level level, BlockPos pos) {
        if (level.getServer() == null) return true;
        if (!ModGameRules.enableStructureProtection(level.getServer())) return true;
        if (level.dimension() == ModDimensions.HTC_LEVEL) return true;
        return !NoHostileSpawnZones.isProtected(level.dimension(), pos.getX(), pos.getY(), pos.getZ());
    }

    /** NINGUNA explosión (ki, TNT, creeper...) rompe bloques protegidos: toda la HTC, y
     *  dentro de las zonas de Kami/Yemma solo sobreviven los bloques puestos por jugadores.
     *  (Antes las explosiones se saltaban la protección: solo se cubría la rotura manual.) */
    @SubscribeEvent
    public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
        Level level = event.getLevel();
        if (level.isClientSide || level.getServer() == null) return;
        if (!ModGameRules.enableStructureProtection(level.getServer())) return;

        if (level.dimension() == ModDimensions.HTC_LEVEL) {
            event.getAffectedBlocks().clear();
            return;
        }

        PlayerPlacedBlocks placed = PlayerPlacedBlocks.get(level.getServer());
        event.getAffectedBlocks().removeIf(pos ->
                NoHostileSpawnZones.isProtected(level.dimension(), pos.getX(), pos.getY(), pos.getZ())
                        && !placed.contains(level.dimension(), pos));
    }
}