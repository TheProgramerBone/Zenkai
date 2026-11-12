package com.hmc.zenkai.feature.rage;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.config.ServerConfig;
import com.hmc.zenkai.feature.combat.StunState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Protección de la secuencia LARGA de la primera transformación (decidido por el usuario el 2026-11-08, para que se sienta como una
 * escena y no como un efecto en mitad de la pelea): mientras dura, el jugador es INVULNERABLE y no puede actuar ni moverse.
 * <ul>
 *   <li>Invulnerable: {@code CombatZenkaiHooks} cancela el golpe entrante y anula el daño si llega a {@code LivingDamageEvent.Pre}. El daño
 *       que atraviesa la invulnerabilidad ({@code /kill}, el vacío) NO se detiene: la protección no puede dejarte atrapado.</li>
 *   <li>Sin acciones: se reutiliza {@link StunState#hold}, que ya corta lo que hiciera, rompe la guardia, inmoviliza y manda el estado al
 *       cliente (su input se anula). Si el servidor apagó {@code stun.affect_players}, el hold no se aplica y solo queda la invulnerabilidad.</li>
 * </ul>
 * Si la causa es una muerte (aliado o mascota) cubre la escena entera (~6,3 s); en cualquier otra primera vez, solo los ticks de la config.
 * Solo se activa en la PRIMERA transformación de cada forma (la misma que dispara la secuencia larga: no se puede repetir a voluntad ni
 * usarla de escudo), con la duración de {@code rage.awakening_protection_ticks} (0 = sin protección). Estado en memoria: un reinicio
 * del servidor la corta, que es el error barato.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID)
public final class AwakeningProtection {
    private AwakeningProtection() {}

    /** UUID -> tick de juego hasta el que está protegido. */
    private static final Map<UUID, Long> UNTIL = new ConcurrentHashMap<>();

    /** Empieza la protección (si la config no la apaga). @return los ticks aplicados (0 = ninguno). */
    public static int start(ServerPlayer sp) { return start(sp, 0); }

    /**
     * @param minTicks mínimo de ticks (si la config no la apaga): el despertar por una MUERTE cubre la escena entera, que arranca en
     *                 el mismo instante que la protección porque ese despertar es automático (sin carga previa)
     */
    public static int start(ServerPlayer sp, int minTicks) {
        int ticks = ServerConfig.RAGE_AWAKENING_PROTECTION_TICKS.get();
        if (ticks <= 0) return 0;
        ticks = Math.max(ticks, minTicks);
        UNTIL.put(sp.getUUID(), sp.level().getGameTime() + ticks);
        try {
            StunState.hold(sp, ticks);
        } catch (RuntimeException ignored) {
            // El hold manda un packet de estado: un fallo de red no debe quitar la invulnerabilidad ni abortar el cambio de forma.
        }
        return ticks;
    }

    /** ¿Está protegido ahora mismo? Caduca solo y se limpia al consultarlo. */
    public static boolean isProtected(Entity e) {
        if (!(e instanceof ServerPlayer sp)) return false;
        Long until = UNTIL.get(sp.getUUID());
        if (until == null) return false;
        if (until <= sp.level().getGameTime()) {
            UNTIL.remove(sp.getUUID());
            return false;
        }
        return true;
    }

    public static void forget(UUID id) { UNTIL.remove(id); }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) { UNTIL.remove(e.getEntity().getUUID()); }

    /** Si aun así muere (daño que atraviesa la invulnerabilidad), la protección no sobrevive a la muerte. */
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent e) { UNTIL.remove(e.getEntity().getUUID()); }
}
