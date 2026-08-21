package com.hmc.zenkai.client.party;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.feature.party.PartySyncPacket;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Caché cliente del último PartySyncPacket recibido. Un solo campo porque cada paquete
 * es el estado COMPLETO (ver PartySyncPacket), no hay nada que fusionar. Consumido por
 * PartyScreen.
 * Se limpia al entrar/salir de una partida — mismo patrón que RaceLayerDiscovery — para
 * que la party de un server no sobreviva visualmente a la conexión a otro.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public final class ClientPartyState {
    private ClientPartyState() {}

    private static volatile PartySyncPacket last = null;

    public static void onSync(PartySyncPacket pkt) { last = pkt.inParty() ? pkt : null; }

    @Nullable
    public static PartySyncPacket current() { return last; }

    private static void clear() { last = null; }

    @SubscribeEvent
    public static void onLogin(ClientPlayerNetworkEvent.LoggingIn e) { clear(); }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut e) { clear(); }
}
