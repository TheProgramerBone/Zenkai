package com.hmc.zenkai.worldgen;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.core.ModGameRules;
import com.hmc.zenkai.core.network.feature.player.OtherworldManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
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
        Level level = player.level();
        if (level.isClientSide || level.getServer() == null) return;

        BlockPos pos = event.getPos();
        boolean inZone = NoHostileSpawnZones.isProtected(
                level.dimension(), pos.getX(), pos.getY(), pos.getZ());

        // Más allá: FUERA de las zonas del palacio no se construye (creativo sí puede).
        // Nota: no depende de la gamerule de protección, es una regla propia del más allá.
        if (!player.isCreative() && player instanceof ServerPlayer sp
                && OtherworldManager.isInOtherworld(sp) && !inZone) {
            event.setCanceled(true);
            resyncAfterCancel(player, pos);
            return;
        }

        if (isProtectedArea(level, pos)) return;

        PlayerPlacedBlocks data = PlayerPlacedBlocks.get(level.getServer());
        if (player.isCreative()) {
            // Creativo construye ESTRUCTURA: no se registra como "del jugador", así que en
            // survival no se puede romper. Limpiamos cualquier registro previo en esa posición.
            data.remove(level.dimension(), pos);
            return;
        }
        data.add(level.dimension(), pos);
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        Player player = event.getPlayer();
        Level level = player.level();
        if (level.isClientSide || level.getServer() == null) return;

        BlockPos pos = event.getPos();
        if (isProtectedArea(level, pos)) return; // fuera de zona: reglas normales

        PlayerPlacedBlocks data = PlayerPlacedBlocks.get(level.getServer());
        if (player.isCreative()) {
            data.remove(level.dimension(), pos); // limpieza: la posición vuelve a ser estructura
            return;
        }

        if (data.contains(level.dimension(), pos)) {
            data.remove(level.dimension(), pos);  // era del jugador → se puede romper
        } else {
            event.setCanceled(true);              // bloque original de la estructura → protegido
            resyncAfterCancel(player, pos);

            String protector = NoHostileSpawnZones.getProtector(
                    level.dimension(), pos.getX(), pos.getY(), pos.getZ());
            player.displayClientMessage(
                    protector != null
                            ? Component.translatable("messages.zenkai.cannot_break",
                            Component.translatable(protector))
                            : Component.translatable("messages.zenkai.cannot_break_generic"),
                    true);
        }
    }

    /**
     * Sincroniza al cliente tras cancelar una colocación/rotura: el cliente predice el cambio
     * (bloque puesto/roto + ítem descontado) y sin esto queda desincronizado en multijugador.
     */
    private static void resyncAfterCancel(net.minecraft.world.entity.player.Player player, BlockPos pos) {
        if (!(player instanceof net.minecraft.server.level.ServerPlayer sp)) return;
        sp.connection.send(new net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket(sp.level(), pos));
        for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
            sp.connection.send(new net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket(sp.level(), pos.relative(d)));
        }
        sp.containerMenu.sendAllDataToRemote();
    }

    private static boolean isProtectedArea(Level level, BlockPos pos) {
        if (level.getServer() == null) return true;
        if (!ModGameRules.enableStructureProtection(level.getServer())) return true;
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