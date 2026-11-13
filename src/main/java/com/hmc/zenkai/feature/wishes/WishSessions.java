package com.hmc.zenkai.feature.wishes;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.content.entity.overworld.ShenLongEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sesión de deseo: el servidor recuerda QUÉ Shenlong atendió a cada jugador, y cada handler de deseo la
 * exige antes de conceder nada.
 *
 * POR QUÉ EXISTE. Los paquetes de deseo (TP, inmortalidad, Legendario, revivir, aldeano, abrir el menú de
 * ítems) solo comprobaban el toggle de config. Un cliente modificado podía mandarlos sin haber invocado
 * jamás a Shenlong: 1000 TP por paquete, sin cooldown ni pool, o el menú de «quiero más de…» abierto a
 * voluntad. La pantalla solo ESCONDE lo que no puedes pedir; la autoridad es esta sesión.
 *
 * REGLAS. Se abre en {@code ShenLongEntity.mobInteract} (el único camino legítimo a la pantalla) y se cierra
 * en {@link WishFinalizer#finalizeWish} (un deseo por interacción: para pedir otro hay que volver a hablar
 * con el dragón). Es válida mientras el dragón siga vivo, con deseos en el pool, a ≤ {@link #MAX_DIST}
 * bloques (el mismo radio con el que WishFinalizer lo busca) y durante {@link #TIMEOUT_TICKS} como máximo.
 * Vive en memoria: un reinicio del servidor la borra, y es lo correcto, porque el dragón tampoco sobrevive
 * a un reinicio con la pantalla abierta.
 *
 * EXCLUSIVIDAD. Un dragón atiende a UN jugador a la vez: {@link #tryOpen} rechaza a cualquier otro mientras
 * la sesión del primero siga viva. La sesión termina cuando el jugador PIDE un deseo (WishFinalizer cierra también
 * la pantalla, así con varios deseos hay que volver a hablarle y otros pueden colarse entre medias), cuando CIERRA
 * las pantallas de deseo ({@link WishScreenClosedPayload}) o, como red de seguridad contra clientes que no avisan,
 * por tiempo, distancia o desconexión.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID)
public final class WishSessions {
    private WishSessions() {}

    /** Mismo radio que WishFinalizer usa para encontrar al dragón. */
    public static final double MAX_DIST = 32.0;
    /** Cuánto puede tener un jugador abierta la sesión sin conceder nada: 5 minutos. */
    public static final long TIMEOUT_TICKS = 6000L;

    private record Session(UUID dragon, long openedTick) {}

    private static final Map<UUID, Session> OPEN = new ConcurrentHashMap<>();

    /**
     * Abre la sesión de {@code player} con {@code dragon}. Devuelve false si OTRO jugador está atendido ahora
     * mismo por ese dragón (el que llama debe avisarle con {@code messages.zenkai.shenron_busy}).
     */
    public static boolean tryOpen(ServerPlayer player, ShenLongEntity dragon) {
        long now = dragon.level().getGameTime();
        for (Map.Entry<UUID, Session> e : OPEN.entrySet()) {
            if (e.getKey().equals(player.getUUID()) || !e.getValue().dragon().equals(dragon.getUUID())) continue;
            ServerPlayer other = player.server.getPlayerList().getPlayer(e.getKey());
            if (other != null && isLive(other, e.getValue(), dragon, now)) return false;
            OPEN.remove(e.getKey()); // sesión caducada o de alguien que ya no está: se libera al dragón
        }
        OPEN.put(player.getUUID(), new Session(dragon.getUUID(), now));
        return true;
    }

    private static boolean isLive(ServerPlayer player, Session s, ShenLongEntity dragon, long now) {
        return dragon.isAlive()
                && dragon.getWishesRemaining() > 0
                && player.level() == dragon.level()
                && now - s.openedTick() <= TIMEOUT_TICKS
                && dragon.distanceToSqr(player) <= MAX_DIST * MAX_DIST;
    }

    /** El dragón con el que habla el jugador, o null si no tiene sesión abierta (llamar tras {@link #validate}). */
    @org.jetbrains.annotations.Nullable
    public static ShenLongEntity dragonOf(ServerPlayer player) {
        Session s = OPEN.get(player.getUUID());
        return s != null && player.level() instanceof ServerLevel level
                && level.getEntity(s.dragon()) instanceof ShenLongEntity d ? d : null;
    }

    public static void close(ServerPlayer player) {
        OPEN.remove(player.getUUID());
    }

    /**
     * ¿Tiene el jugador una sesión de deseo válida? Si no, le avisa y borra la sesión. Llamar al PRINCIPIO de
     * cada handler de deseo (después de leer el jugador, antes de tocar nada).
     */
    public static boolean validate(ServerPlayer player) {
        Session s = OPEN.get(player.getUUID());
        if (s != null
                && player.level() instanceof ServerLevel level
                && level.getEntity(s.dragon()) instanceof ShenLongEntity dragon
                && isLive(player, s, dragon, level.getGameTime())) {
            return true;
        }
        OPEN.remove(player.getUUID());
        player.displayClientMessage(Component.translatable("messages.zenkai.no_open_wish"), false);
        return false;
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        OPEN.remove(event.getEntity().getUUID());
    }
}
