package com.hmc.zenkai.feature.kichamber;

import net.minecraft.world.entity.player.Player;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Qué JUGADORES están ahora sujetos dentro de una cámara de contención de ki, en servidor. Lo
 * escribe solo el block entity (al atrapar y al soltar) y lo leen: las reglas de acción (dentro no
 * se pelea ni se lanzan técnicas, ver ServerActionContext), KiChargeSystem y RegenSystem (dentro
 * no se recupera ki) y KiChargePacket (la tecla de cargar no manda).
 *
 * POR QUÉ UN REGISTRO APARTE y no fiarse de la sujeción (StunState): con stun.affect_players
 * apagado un jugador no queda aturdido, y las reglas de ki no tienen por qué saber de aturdimientos.
 * No se guarda en disco: una sesión no sobrevive a un reinicio.
 *
 * Además fuerza que el clic derecho AGACHADO llegue al bloque aunque se lleve un ítem en la mano
 * (vanilla lo desvía al ítem): agachado + clic derecho es cómo se abre la pantalla.
 */
@net.neoforged.fml.common.EventBusSubscriber(modid = com.hmc.zenkai.Zenkai.MOD_ID)
public final class KiChamberSessions {
    private KiChamberSessions() {}

    private static final Set<UUID> INSIDE = ConcurrentHashMap.newKeySet();

    public static void enter(Player p) { INSIDE.add(p.getUUID()); }
    public static void leave(UUID id)  { INSIDE.remove(id); }

    public static boolean isInside(Player p) { return INSIDE.contains(p.getUUID()); }

    @net.neoforged.bus.api.SubscribeEvent
    public static void onRightClickBlock(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock e) {
        if (e.getEntity().isShiftKeyDown()
                && e.getLevel().getBlockState(e.getPos()).is(com.hmc.zenkai.registry.ModBlocks.KI_CONTAINMENT_CHAMBER.get())) {
            e.setUseBlock(net.neoforged.neoforge.common.util.TriState.TRUE);
            e.setUseItem(net.neoforged.neoforge.common.util.TriState.FALSE);
        }
    }

    /** Se desconecta sujeto: que no vuelva con la pose de cargar puesta ni figure dentro. */
    @net.neoforged.bus.api.SubscribeEvent
    public static void onLogout(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent e) {
        if (INSIDE.remove(e.getEntity().getUUID())) {
            com.hmc.zenkai.feature.player.PlayerStatsAttachment.get(e.getEntity()).setChargingKi(false);
        }
    }
}
