package com.hmc.zenkai.client.party;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.config.ClientConfig;
import com.hmc.zenkai.feature.party.PartyHudSelectionPacket;
import com.hmc.zenkai.feature.party.PartySyncPacket;
import com.hmc.zenkai.feature.party.PartyVitalsPacket;
import com.hmc.zenkai.feature.party.PartyVitalsServer;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Caché cliente del último PartySyncPacket recibido. Un solo campo porque cada paquete
 * es el estado COMPLETO (ver PartySyncPacket), no hay nada que fusionar. Consumido por
 * PartyScreen.
 * Se limpia al entrar/salir de una partida — mismo patrón que RaceLayerDiscovery — para
 * que la party de un server no sobreviva visualmente a la conexión a otro.
 *
 * También guarda lo del PANEL DE PARTY (PartyHudOverlay): los signos vitales que manda el
 * servidor (PartyVitalsPacket) y la elección de a quién mostrar. La elección vive en ClientConfig
 * (por jugador, entre partidas) y aquí se resuelve contra la party actual: lo que ya no es miembro
 * se ignora, y mientras el jugador no haya elegido nada el panel se llena solo con los primeros
 * compañeros.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public final class ClientPartyState {
    private ClientPartyState() {}

    private static volatile PartySyncPacket last = null;

    /** Último estado conocido de cada compañero elegido: {body, stamina, ki} en milésimas. */
    private static final Map<UUID, int[]> VITALS = new ConcurrentHashMap<>();

    public static void onSync(PartySyncPacket pkt) {
        last = pkt.inParty() ? pkt : null;
        VITALS.clear();
        // La party cambió (o acabamos de entrar): el servidor no recuerda a quién queremos ver.
        pushSelection();
    }

    @Nullable
    public static PartySyncPacket current() { return last; }

    public static void onVitals(PartyVitalsPacket pkt) {
        VITALS.clear();
        for (PartyVitalsPacket.Vital v : pkt.vitals()) VITALS.put(v.id(), new int[]{v.body(), v.stamina(), v.ki()});
    }

    /** {body, stamina, ki} en milésimas (-1 = sin esa reserva) o null si aún no ha llegado. */
    @Nullable
    public static int[] vitalsOf(UUID id) { return VITALS.get(id); }

    /**
     * Compañeros que se dibujan en el panel, en orden: la elección guardada filtrada por la party
     * actual, o —si nunca se eligió— los primeros miembros que no sean el propio jugador.
     */
    public static List<PartySyncPacket.Member> shownMembers() {
        PartySyncPacket st = last;
        List<PartySyncPacket.Member> out = new ArrayList<>();
        if (st == null) return out;
        UUID me = Minecraft.getInstance().player == null ? null : Minecraft.getInstance().player.getUUID();

        List<UUID> chosen = ClientConfig.partyHudSelected();
        if (chosen.isEmpty()) {
            for (PartySyncPacket.Member m : st.members()) {
                if (m.id().equals(me)) continue;
                out.add(m);
                if (out.size() >= PartyVitalsServer.MAX_SHOWN) break;
            }
            return out;
        }
        for (UUID id : chosen) {
            for (PartySyncPacket.Member m : st.members()) {
                if (m.id().equals(id) && !m.id().equals(me)) { out.add(m); break; }
            }
            if (out.size() >= PartyVitalsServer.MAX_SHOWN) break;
        }
        return out;
    }

    /** Posición (0-3) de este miembro en el panel, o -1 si no está elegido. Da el color de su diamante. */
    public static int slotOf(UUID id) {
        List<PartySyncPacket.Member> shown = shownMembers();
        for (int i = 0; i < shown.size(); i++) if (shown.get(i).id().equals(id)) return i;
        return -1;
    }

    /** Elige o quita a un compañero del panel (tope {@link PartyVitalsServer#MAX_SHOWN}). */
    public static void toggleShown(UUID id) {
        List<UUID> ids = new ArrayList<>();
        for (PartySyncPacket.Member m : shownMembers()) ids.add(m.id());
        if (!ids.remove(id)) {
            if (ids.size() >= PartyVitalsServer.MAX_SHOWN) return;
            ids.add(id);
        }
        // Una lista vacía guardada como "" volvería al autollenado: se guarda un centinela inválido.
        ClientConfig.setPartyHudSelected(ids.isEmpty() ? List.of(new UUID(0L, 0L)) : ids);
        pushSelection();
    }

    /** Manda al servidor a quién queremos ver, para que solo mande esos vitales. */
    public static void pushSelection() {
        Minecraft mc = Minecraft.getInstance();
        if (last == null || mc.getConnection() == null) return;
        List<UUID> ids = new ArrayList<>();
        for (PartySyncPacket.Member m : shownMembers()) ids.add(m.id());
        PacketDistributor.sendToServer(new PartyHudSelectionPacket(ids));
    }

    private static void clear() { last = null; VITALS.clear(); }

    @SubscribeEvent
    public static void onLogin(ClientPlayerNetworkEvent.LoggingIn e) { clear(); }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut e) { clear(); }
}
