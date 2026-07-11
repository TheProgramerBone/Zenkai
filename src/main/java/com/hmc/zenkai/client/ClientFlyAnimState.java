package com.hmc.zenkai.client;

import com.hmc.zenkai.client.ZenkaiPalAnimations.FlyDir;
import com.hmc.zenkai.core.network.feature.ki.FlyAnimPacket;
import net.minecraft.client.multiplayer.ClientLevel;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lado CLIENTE de la sincronización de animaciones de vuelo.
 *
 *  - Remotos: mapa entityId -> (volando, dir, boost) alimentado por FlyAnimSyncPacket;
 *    ClientZenkaiPalTick lo consulta para mover el estado-máquina de cada jugador remoto.
 *  - Local: sendIfChanged() publica el estado propio SOLO al cambiar (edge-trigger, mismo
 *    patrón que lastFlyBoostSent).
 */
public final class ClientFlyAnimState {
    private ClientFlyAnimState() {}

    public record Remote(boolean flying, FlyDir dir, boolean boosting) {}

    private static final FlyDir[] DIRS = FlyDir.values();
    private static final Map<Integer, Remote> REMOTES = new ConcurrentHashMap<>();

    // Último estado propio enviado (edge-trigger).
    private static boolean lastFlying = false;
    private static FlyDir lastDir = null;
    private static boolean lastBoosting = false;

    /** Estado sincronizado de un jugador remoto, o null. */
    public static Remote get(int entityId) {
        return REMOTES.get(entityId);
    }

    /** Desde FlyAnimSyncPacket. */
    public static void onSync(int entityId, boolean flying, byte dir, boolean boosting) {
        if (!flying) {
            REMOTES.remove(entityId);
            return;
        }
        int i = Math.min(Math.max(dir, 0), DIRS.length - 1);
        REMOTES.put(entityId, new Remote(true, DIRS[i], boosting));
    }

    /** Publica el estado del jugador LOCAL solo si cambió. dir puede ser null si flying=false. */
    public static void sendIfChanged(boolean flying, FlyDir dir, boolean boosting) {
        if (flying == lastFlying && dir == lastDir && boosting == lastBoosting) return;
        lastFlying = flying;
        lastDir = dir;
        lastBoosting = boosting;
        PacketDistributor.sendToServer(new FlyAnimPacket(
                flying, (byte) (dir == null ? 0 : dir.ordinal()), boosting));
    }

    /** Poda de entidades que ya no existen (llamar del tick cliente, junto a la de STATES). */
    public static void prune(ClientLevel level) {
        REMOTES.keySet().removeIf(id -> level.getEntity(id) == null);
    }

    /** Al cambiar de mundo/desconectar. */
    public static void reset() {
        REMOTES.clear();
        lastFlying = false;
        lastDir = null;
        lastBoosting = false;
    }
}