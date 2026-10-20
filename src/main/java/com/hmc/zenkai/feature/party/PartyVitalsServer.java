package com.hmc.zenkai.feature.party;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Servidor del panel de party: recuerda qué compañeros quiere ver cada jugador (ver
 * PartyHudSelectionPacket) y les manda sus signos vitales cada {@link #INTERVAL} ticks.
 *
 * Autoridad: en cada envío se vuelve a comprobar que quien pide y el pedido siguen en LA MISMA
 * party y que el pedido está conectado; así una selección vieja (alguien expulsado, otra party) no
 * filtra la vida de un extraño. Solo se manda lo que se dibuja: milésimas, sin cifras absolutas.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID)
public final class PartyVitalsServer {
    private PartyVitalsServer() {}

    /** Máximo de compañeros visibles en el panel (el resto de la party no ocupa pantalla). */
    public static final int MAX_SHOWN = 4;
    /** Medio segundo: suficiente para una barra, y 4 filas por jugador es poco tráfico. */
    private static final int INTERVAL = 10;

    /** Compañeros FALSOS de depuración (/zenkai debug party): un máximo de 8, ids deterministas. */
    private static final int FAKE_MAX = 8;

    public static UUID fakeId(int i) {
        return UUID.nameUUIDFromBytes(("zenkai-fake-party-" + i).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /** Nombre del compañero falso, o null si ese UUID no es uno de ellos. */
    public static String fakeName(UUID id) {
        for (int i = 1; i <= FAKE_MAX; i++) if (fakeId(i).equals(id)) return "Fake" + i;
        return null;
    }

    /** Signos vitales inventados que oscilan, para ver las barras moverse. */
    private static PartyVitalsPacket.Vital fakeVital(UUID id, long tick) {
        int idx = 0;
        for (int i = 1; i <= FAKE_MAX; i++) if (fakeId(i).equals(id)) idx = i;
        double t = tick / 20.0 + idx * 1.7;
        return new PartyVitalsPacket.Vital(id,
                (int) (500 + 450 * Math.sin(t * 0.5)),
                (int) (500 + 450 * Math.sin(t * 0.8 + 1)),
                (int) (500 + 450 * Math.sin(t * 0.3 + 2)));
    }

    private static final Map<UUID, List<UUID>> SELECTION = new ConcurrentHashMap<>();

    public static void setSelection(ServerPlayer sp, List<UUID> ids) {
        List<UUID> copy = new ArrayList<>();
        for (UUID id : ids) {
            if (copy.size() >= MAX_SHOWN) break;
            if (!id.equals(sp.getUUID()) && !copy.contains(id)) copy.add(id);
        }
        if (copy.isEmpty()) SELECTION.remove(sp.getUUID());
        else SELECTION.put(sp.getUUID(), copy);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post e) {
        MinecraftServer server = e.getServer();
        if (server.getTickCount() % INTERVAL != 0 || SELECTION.isEmpty()) return;

        PartyManager pm = PartyManager.get(server);
        for (Map.Entry<UUID, List<UUID>> en : SELECTION.entrySet()) {
            ServerPlayer viewer = server.getPlayerList().getPlayer(en.getKey());
            if (viewer == null) continue;
            PartyManager.Party party = pm.partyOf(viewer.getUUID());
            if (party == null) continue;

            List<PartyVitalsPacket.Vital> out = new ArrayList<>(MAX_SHOWN);
            for (UUID id : en.getValue()) {
                if (!party.members.contains(id)) continue;
                ServerPlayer target = server.getPlayerList().getPlayer(id);
                if (target == null) {
                    if (fakeName(id) != null) out.add(fakeVital(id, server.getTickCount()));
                    continue;
                }
                out.add(vitalOf(target));
            }
            PacketDistributor.sendToPlayer(viewer, new PartyVitalsPacket(out));
        }
    }

    private static PartyVitalsPacket.Vital vitalOf(ServerPlayer p) {
        PlayerStatsAttachment att = PlayerStatsAttachment.get(p);
        if (!att.isRaceChosen()) {
            // Sin raza: solo la vida vanilla; no tiene stamina ni ki que enseñar.
            return new PartyVitalsPacket.Vital(p.getUUID(),
                    milli(p.getHealth(), p.getMaxHealth()), -1, -1);
        }
        return new PartyVitalsPacket.Vital(p.getUUID(),
                milli(att.getBody(), att.getBodyMax()),
                milli(att.getStamina(), att.getStaminaMax()),
                milli(att.getEnergy(), att.getEnergyMax()));
    }

    private static int milli(double cur, double max) {
        if (max <= 0) return -1;
        return (int) Math.round(Math.max(0.0, Math.min(1.0, cur / max)) * 1000.0);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        SELECTION.remove(e.getEntity().getUUID());
    }
}
