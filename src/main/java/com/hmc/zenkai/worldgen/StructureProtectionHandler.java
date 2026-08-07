package com.hmc.zenkai.worldgen;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.registry.ModGameRules;
import com.hmc.zenkai.feature.player.OtherworldManager;
import com.hmc.zenkai.registry.ModDimensions;
import com.hmc.zenkai.registry.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
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

    /**
     * Altura por debajo de la cual el Otherworld deja de ser "el más allá" y pasa a ser HFIL,
     * o sea subsuelo minable. Las islas viven de y=130 a 190 y HFIL de -60 a 120, así que el
     * corte cae en tierra de nadie.
     * Existe porque prohibir construir en toda la dimensión hacía imposible minar el katchin,
     * que genera ahí abajo: sin antorchas ni puentes no se baja a una veta.
     */
    private static final int OTHERWORLD_SURFACE_Y = 128;

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        Level level = player.level();
        if (level.isClientSide || level.getServer() == null) return;

        BlockPos pos = event.getPos();
        boolean inZone = ProtectedZones.isProtected((net.minecraft.server.level.ServerLevel) level, pos);

        // Arriba (islas, palacio de Yemma) no se construye fuera de las zonas. Abajo (HFIL)
        // sí: es la mina. La comprobación de dimensión va aparte del flag porque el flag
        // sigue puesto mientras el jugador está muerto, y sin ella bloquearía también la
        // construcción en cualquier otro sitio al que acabara yendo con el flag activo.
        if (!player.isCreative() && player instanceof ServerPlayer sp
                && OtherworldManager.isInOtherworld(sp)
                && sp.level().dimension().equals(ModDimensions.OTHERWORLD_LEVEL)
                && pos.getY() >= OTHERWORLD_SURFACE_Y
                && !inZone) {
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

            String protector = ProtectedZones.protectorAt(
                    (net.minecraft.server.level.ServerLevel) level, pos);
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
        if (ModGameRules.enableStructureProtection(level.getServer())) return true;
        return !ProtectedZones.isProtected((net.minecraft.server.level.ServerLevel) level, pos);
    }

    /** NINGUNA explosión (ki, TNT, creeper...) rompe bloques protegidos: toda la HTC, y
     *  dentro de las zonas de Kami/Yemma solo sobreviven los bloques puestos por jugadores.
     *  (Antes las explosiones se saltaban la protección: solo se cubría la rotura manual.) */
    @SubscribeEvent
    public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
        Level level = event.getLevel();
        if (level.isClientSide || level.getServer() == null) return;

        // Katchin: ninguna explosión lo mueve, ni ki ni TNT ni creeper. Va aquí arriba
        // porque es una propiedad del MATERIAL, no de la zona, y no depende de gamerules.
        event.getAffectedBlocks().removeIf(pos ->
                level.getBlockState(pos).is(ModTags.Blocks.KI_INDESTRUCTIBLE));

        if (ModGameRules.enableStructureProtection(level.getServer())) return;
        if (level.dimension() == ModDimensions.HTC_LEVEL) {
            event.getAffectedBlocks().clear();
            return;
        }

        PlayerPlacedBlocks placed = PlayerPlacedBlocks.get(level.getServer());
        event.getAffectedBlocks().removeIf(pos ->
                ProtectedZones.isProtected((ServerLevel) level, pos)
                        && !placed.contains(level.dimension(), pos));
    }
}