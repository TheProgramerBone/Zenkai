package com.hmc.zenkai.worldgen;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.core.ModGameRules;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Muestra en la hotbar (actionbar), en amarillo, "Esta zona está protegida por: X"
 * cuando el jugador ENTRA a una zona protegida (transición fuera→dentro), lo que
 * también le marca dónde empieza la zona segura.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID)
public final class ProtectedZoneMessageHandler {
    private ProtectedZoneMessageHandler() {}

    /** Última zona en la que estaba cada jugador (por UUID). */
    private static final Map<UUID, String> LAST_ZONE = new HashMap<>();

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Level level = player.level();
        if (level.isClientSide) return;

        // Si la protección está desactivada por gamerule, no avisamos.
        if (level.getServer() != null && ModGameRules.enableStructureProtection(level.getServer())) {
            LAST_ZONE.remove(player.getUUID());
            return;
        }

        String current = NoHostileSpawnZones.getProtector(
                level.dimension(), player.getX(), player.getY(), player.getZ());
        String prev = LAST_ZONE.get(player.getUUID());

        // Entró (o cambió de zona): avisar una vez.
        if (current != null && !current.equals(prev)) {
            player.displayClientMessage(
                    Component.translatable("messages.zenkai.zone_protected", current)
                            .withStyle(ChatFormatting.YELLOW),
                    true); // true = actionbar (hotbar)
        }

        if (current == null) LAST_ZONE.remove(player.getUUID());
        else LAST_ZONE.put(player.getUUID(), current);
    }
}