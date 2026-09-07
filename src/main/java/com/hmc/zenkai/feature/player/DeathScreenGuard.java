package com.hmc.zenkai.feature.player;

import com.hmc.zenkai.Zenkai;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * INVARIANTE DURA: mientras el cliente de un jugador esté en la pantalla de muerte, su vida
 * vanilla en el servidor DEBE seguir en 0.
 *
 * El porqué está en vanilla, no en este mod. ServerGamePacketListenerImpl#handleClientCommand
 * atiende el botón "Reaparecer" así:
 *
 *     case PERFORM_RESPAWN:
 *         ...
 *         if (this.player.getHealth() &gt; 0.0F) return;   // se ignora la peticion, en silencio
 *         this.player = this.server.getPlayerList().respawn(...);
 *
 * O sea: cualquier cosa que le suba la vida por encima de 0 DESPUÉS de que el paquete de muerte
 * ya salió hacia el cliente deja al jugador clavado en la pantalla de muerte para siempre, sin
 * error, sin log y sin nada raro en pantalla — el botón simplemente no hace nada.
 *
 * Y ese estado es fácil de alcanzar sin querer, por dos rarezas de vanilla que conviene tener
 * delante antes de tocar cualquier cosa de muerte:
 *  1) ServerPlayer#die NO llama a super.die(), así que para un JUGADOR el flag `dead` de
 *     LivingEntity nunca se pone a true. isDeadOrDying() es, literalmente, getHealth() &lt;= 0.
 *     Por eso un solo setHealth(&gt;0) no solo rompe el respawn: además desarma de golpe cada
 *     guardia del mod que se apoya en isDeadOrDying() (mirrorHealth, el corte del tick...).
 *  2) ServerPlayer#die tampoco tiene guardia de reentrada, así que se puede ejecutar entera
 *     dos veces para la misma muerte (LivingEntity#hurt vuelve a llamar a die() al terminar si
 *     ve isDeadOrDying(), y ahí ya se envió el paquete de muerte en la primera pasada).
 *
 * Este marcador cierra la CLASE de fallo entera en vez de una de sus causas: se pone en cuanto
 * una muerte de jugador sobrevive sin cancelarse, y mientras esté puesto nadie escribe vida
 * &gt; 0 (ni DownedDeathGuard, ni PlayerLifeCycle.mirrorHealth, ni un Totem of Undying tardío)
 * y ninguna segunda muerte llega a repetir el paquete, el mensaje ni el drop del inventario.
 * Se limpia al reaparecer, al desconectar y en OtherworldManager.fullHeal (la única vía
 * legítima por la que a un jugador se le devuelve la vida sin pasar por el respawn).
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID)
public final class DeathScreenGuard {
    private DeathScreenGuard() {}

    /** Jugadores cuya muerte ya se consumó y que están esperando a pulsar "Reaparecer". */
    private static final Set<UUID> AWAITING_RESPAWN = ConcurrentHashMap.newKeySet();

    // LOWEST: el último de la fila. Si llegamos hasta aquí es que nadie canceló el evento, o
    // sea que esta muerte VA A OCURRIR de verdad y el paquete de muerte saldrá hacia el
    // cliente. Las muertes canceladas (derribado, inmortalidad, re-ancla en el Otro Mundo) ni
    // siquiera invocan este listener, porque no se piden eventos cancelados.
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) {
            AWAITING_RESPAWN.add(sp.getUUID());
        }
    }

    // Quien se desconecta SIN haber pulsado "Reaparecer" vuelve a entrar con la vida a 0
    // guardada en su NBT y con el cliente otra vez en la pantalla de muerte, pero el marcador
    // se perdió con la sesión anterior. Se rehace aquí para que la invariante siga cubriéndolo.
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp && sp.getHealth() <= 0.0F) {
            AWAITING_RESPAWN.add(sp.getUUID());
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent e) {
        AWAITING_RESPAWN.remove(e.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        AWAITING_RESPAWN.remove(e.getEntity().getUUID());
    }

    /** ¿Este jugador está muerto de verdad y esperando respawn? */
    public static boolean isAwaitingRespawn(Player p) {
        return AWAITING_RESPAWN.contains(p.getUUID());
    }

    /** Lo llama quien devuelve a la vida a un jugador SIN pasar por el respawn de vanilla
     *  (OtherworldManager.fullHeal). A partir de ahí su vida vuelve a ser escribible. */
    public static void forget(UUID id) {
        AWAITING_RESPAWN.remove(id);
    }

    /**
     * Corte del tick de jugador + aplicación de la invariante. Si algo consiguió subirle la
     * vida mientras esperaba respawn, se la devuelve a 0 y lo deja en el log: el jugador
     * recupera el botón "Reaparecer" en ese mismo tick en vez de quedarse encerrado, y queda
     * constancia de que existe una vía de escritura de vida que este guardia no cubre en su
     * origen.
     * @return true si hay que cortar el tick (jugador esperando respawn).
     */
    public static boolean holdDead(Player p) {
        if (!(p instanceof ServerPlayer sp) || !AWAITING_RESPAWN.contains(sp.getUUID())) {
            return false;
        }
        if (sp.getHealth() > 0.0F) {
            Zenkai.LOGGER.warn(
                    "[Zenkai] {} esperaba respawn con {} de vida: algo le escribio vida > 0 tras "
                            + "morir y vanilla habria ignorado el boton Reaparecer. Forzada a 0.",
                    sp.getGameProfile().getName(), sp.getHealth());
            sp.setHealth(0.0F);
        }
        return true;
    }
}
