package com.hmc.zenkai.client;

import com.hmc.zenkai.feature.ki.FlyAnimPacket;
import net.minecraft.client.multiplayer.ClientLevel;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lado CLIENTE de la sincronización de animaciones de vuelo.
 *  - Remotos: mapa entityId -> (volando, boost) alimentado por FlyAnimSyncPacket;
 *    ClientZenkaiPalTick lo consulta para mover el estado-máquina de cada jugador remoto.
 *  - Local: sendIfChanged() publica el estado propio SOLO al cambiar (edge-trigger, mismo
 *    patrón que lastFlyBoostSent).
 * La DIRECCIÓN desapareció con el rediseño del vuelo: hay una sola postura de crucero y una
 * de boost, y la orientación la calcula el código a partir de la mirada y el movimiento real.
 * Sincronizar una dirección discreta ya no aporta nada.
 * DEUDA: FlyAnimPacket / FlyAnimSyncPacket / FlyAnimServerState siguen llevando el byte de
 * dirección, ahora siempre 0. Se mantiene para no tocar tres archivos de red por un campo
 * muerto; limpieza pendiente en un diff aparte.
 */
public final class ClientFlyAnimState {
    private ClientFlyAnimState() {}

    public record Remote(boolean flying, boolean boosting) {}

    private static final Map<Integer, Remote> REMOTES = new ConcurrentHashMap<>();

    // Último estado propio enviado (edge-trigger).
    private static boolean lastFlying = false;
    private static boolean lastBoosting = false;

    /** Estado sincronizado de un jugador remoto, o null. */
    public static Remote get(int entityId) {
        return REMOTES.get(entityId);
    }

    /** Desde FlyAnimSyncPacket. El byte de dirección se ignora (ver DEUDA arriba). */
    public static void onSync(int entityId, boolean flying, byte dir, boolean boosting) {
        if (!flying) {
            REMOTES.remove(entityId);
            return;
        }
        REMOTES.put(entityId, new Remote(true, boosting));
    }

    /** Publica el estado del jugador LOCAL solo si cambió. */
    public static void sendIfChanged(boolean flying, boolean boosting) {
        if (flying == lastFlying && boosting == lastBoosting) return;
        lastFlying = flying;
        lastBoosting = boosting;
        PacketDistributor.sendToServer(new FlyAnimPacket(flying, (byte) 0, boosting));
    }

    /** Poda de entidades que ya no existen (llamar del tick cliente, junto a la de STATES). */
    public static void prune(ClientLevel level) {
        REMOTES.keySet().removeIf(id -> level.getEntity(id) == null);
    }

    /** Al cambiar de mundo/desconectar. */
    public static void reset() {
        REMOTES.clear();
        lastFlying = false;
        lastBoosting = false;
    }
}