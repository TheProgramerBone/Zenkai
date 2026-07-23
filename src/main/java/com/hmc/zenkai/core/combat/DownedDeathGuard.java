package com.hmc.zenkai.core.combat;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.CombatZenkaiHooks;
import com.hmc.zenkai.core.ModGameRules;
import com.hmc.zenkai.core.network.feature.player.PlayerLifeCycle;
import com.hmc.zenkai.core.network.feature.player.PlayerStatsAttachment;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Red de seguridad del estado DERRIBADO.
 * CombatZenkaiHooks.onBodyDepleted solo corre cuando llega un golpe por LivingDamageEvent.
 * Lo que mate al jugador por otra vía (vacío, /kill, /damage, efectos, otro mod, un
 * setHealth ajeno) se saltaba el derribado y lo mandaba a morir de verdad.
 * LivingEntity.die() es el embudo por el que pasan TODAS las muertes, y NeoForge lo expone
 * con LivingDeathEvent cancelable, así que no hace falta mixin: se cancela la muerte, se
 * devuelve la vida vanilla a tope (si se queda en 0 el jugador vuelve a morir cada tick) y
 * se entra en derribado. A partir de ahí manda TickHandlers.handleDowned, como siempre.
 * OJO con el bucle: handleDowned MATA a propósito cuando se acaban los 5 s. Esa muerte tiene
 * que pasar, o el jugador sería inmortal. Por eso existe allowRealDeath: marca la siguiente
 * muerte de ese jugador como intencional.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID)
public final class DownedDeathGuard {
    private DownedDeathGuard() {}

    /** Jugadores cuya PRÓXIMA muerte es intencional del mod (timeout del derribado). */
    private static final Set<UUID> ALLOW_DEATH = ConcurrentHashMap.newKeySet();

    /** Llamar justo ANTES de un die() provocado por el mod. Se consume en el primer uso. */
    public static void allowRealDeath(ServerPlayer sp) {
        ALLOW_DEATH.add(sp.getUUID());
    }

    public static void forget(UUID id) {
        ALLOW_DEATH.remove(id);
    }

    // HIGHEST: si vamos a cancelar, conviene hacerlo antes de que OtherworldHandler y
    // compañía traten esta muerte como definitiva (los eventos cancelados no les llegan).
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onDeath(LivingDeathEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;

        // Muerte provocada por el propio mod: dejarla pasar y limpiar la marca.
        if (ALLOW_DEATH.remove(sp.getUUID())) return;

        MinecraftServer server = sp.getServer();
        if (server == null || !ModGameRules.enableRaceBoosts(server)) return;

        PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
        if (!att.isRaceChosen()) return;

        // En el otro mundo la muerte sigue su curso normal: allí ya no hay derribado.
        if (att.isInOtherworld()) return;

        // Inmortal: no cae nunca, y no puede quedarse con el body a 0 (barra bugueada).
        if (att.isImmortal()) {
            e.setCanceled(true);
            att.setBody(att.getBodyMax());
            sp.setHealth(sp.getMaxHealth());
            PlayerLifeCycle.sync(sp);
            return;
        }

        e.setCanceled(true);

        // La vida vanilla TIENE que volver a >0: con 0 el jugador vuelve a morir al tick
        // siguiente y el cliente se queda en la pantalla de muerte a medias.
        sp.setHealth(sp.getMaxHealth());
        att.setBody(0);

        if (!att.flags().isDowned()) {
            att.flags().setDowned(true);
            att.flags().setDownedUntil(
                    sp.serverLevel().getGameTime() + CombatZenkaiHooks.DOWNED_TICKS);
        }
        PlayerLifeCycle.sync(sp);
    }
}