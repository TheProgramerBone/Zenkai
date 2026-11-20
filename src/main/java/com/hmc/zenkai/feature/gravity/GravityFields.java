package com.hmc.zenkai.feature.gravity;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Quién está ahora mismo dentro del campo de una cámara de gravedad, y con qué g.
 *
 * Servidor: cada cámara activa refresca a las entidades de su interior (cada 5 ticks) con una
 * caducidad de {@link #TTL} ticks. Si la cámara se rompe, se descarga o se queda sin energía deja
 * de refrescar y la entrada caduca sola: no hace falta avisar a nadie al apagarla.
 *
 * Cliente: WeightSystem se calcula TAMBIÉN en el cliente (espejo de `weightLoad` en
 * KeyBindings y el hub de Training), así que el jugador local necesita saber su g. El servidor se
 * la manda con {@link GravityFieldSyncPacket} solo cuando cambia; aquí queda en {@link #clientLocalG}.
 * Sin clases de cliente a propósito: este archivo lo carga también un servidor dedicado.
 *
 * Solo se toca desde el hilo del servidor (cámaras y tick de jugadores) o el del cliente (el
 * packet se encola con enqueueWork): HashMap sin sincronizar basta.
 */
public final class GravityFields {
    private GravityFields() {}

    /** Ticks que dura una entrada sin refresco (la cámara refresca cada 5). */
    public static final int TTL = 20;

    private record Field(double g, long expiresAt) {}

    private static final Map<UUID, Field> FIELDS = new HashMap<>();
    /** Último valor mandado a cada jugador, para no repetir el packet. */
    private static final Map<UUID, Float> LAST_SENT = new HashMap<>();
    private static long lastPurge;

    /** g del jugador local según el servidor (1 = sin cámara). Lo escribe el packet. */
    private static volatile double clientLocalG = 1.0;

    /** La cámara llama a esto para cada entidad de su interior. Si dos cámaras solapan, manda la mayor. */
    public static void refresh(Entity e, double g, long gameTime) {
        Field cur = FIELDS.get(e.getUUID());
        if (cur != null && cur.expiresAt() >= gameTime && cur.g() > g) return;
        FIELDS.put(e.getUUID(), new Field(g, gameTime + TTL));
    }

    /** g de cámara de esta entidad (1 si no está en ninguna). Lado cliente: solo el jugador local. */
    public static double chamberG(Entity e) {
        if (e == null) return 1.0;
        if (e.level().isClientSide()) return clientLocalG;
        Field f = FIELDS.get(e.getUUID());
        if (f == null) return 1.0;
        if (f.expiresAt() < e.level().getGameTime()) {
            FIELDS.remove(e.getUUID());
            return 1.0;
        }
        return f.g();
    }

    /** Lo llama el tick del jugador en el servidor: manda la g al cliente solo si cambió. */
    public static void syncIfChanged(ServerPlayer sp) {
        float g = (float) chamberG(sp);
        Float last = LAST_SENT.get(sp.getUUID());
        if (last == null ? g == 1.0F : Math.abs(last - g) < 1.0e-4F) return;
        LAST_SENT.put(sp.getUUID(), g);
        PacketDistributor.sendToPlayer(sp, new GravityFieldSyncPacket(g));
        purge(sp.level().getGameTime());
    }

    /** Limpieza al desconectarse (si no, los mapas crecen sin fin en un servidor). */
    public static void forget(UUID id) {
        FIELDS.remove(id);
        LAST_SENT.remove(id);
    }

    /** Lado cliente: al salir del mundo vuelve a 1 para no arrastrar la g a otro servidor. */
    public static void resetClient() {
        clientLocalG = 1.0;
    }

    static void setClientLocalG(double g) {
        clientLocalG = g;
    }

    /** Purga de entradas caducadas de mobs que nadie volvió a preguntar (una vez cada 30 s). */
    private static void purge(long now) {
        if (now - lastPurge < 600) return;
        lastPurge = now;
        Iterator<Map.Entry<UUID, Field>> it = FIELDS.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getValue().expiresAt() < now) it.remove();
        }
    }

    /** Para gametests: limpia el estado entre pruebas. */
    public static void clearForTests() {
        FIELDS.clear();
        LAST_SENT.clear();
    }

    /** ¿El jugador está en el campo de alguna cámara? */
    public static boolean inChamber(Player p) {
        return chamberG(p) > 1.0;
    }
}
