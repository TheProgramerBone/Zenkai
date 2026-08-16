package com.hmc.zenkai.feature.combat;

import com.hmc.zenkai.config.CommonConfig;
import com.hmc.zenkai.feature.player.PlayerLifeCycle;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * "En combate": estado AUTOMÁTICO que se enciende al dar o recibir daño y se apaga solo tras
 * unos segundos sin nada. No confundir con CombatModeServerState, que es el toggle manual de
 * X: aquel es una postura que el jugador elige, este es una situación en la que el jugador
 * se encuentra. Un jugador puede estar en modo combate paseando por su base, y puede estar
 * en combate sin haber pulsado X porque le cayó un esqueleto encima.
energy_generator * SE GUARDA COMO gameTime DE EXPIRACIÓN, no como booleano con contador. Un instante futuro
 * no necesita que nadie lo decremente cada tick: no hay que barrer un mapa por jugador, no
 * se desincroniza si el servidor pierde ticks, y sobrevive al guardado sin más lógica —
 * al recargar la partida el gameTime ya pasó de largo y el estado sale caducado solo.
energy_generator * Vive en PlayerStateFlags y no en un mapa estático como el turbo justamente por eso: al ir
 * dentro del attachment se sincroniza al cliente por el canal que ya existe, y el HUD puede
 * pintar el icono sin un packet nuevo.
 */
public final class InCombatState {
    private InCombatState() {}

    /** Marca a un jugador como en combate y reinicia la cuenta atrás. Servidor. */
    public static void mark(Player p) {
        if (!(p instanceof ServerPlayer sp)) return;
        PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
        if (!att.isRaceChosen()) return;

        long until = sp.level().getGameTime() + CommonConfig.inCombatTicks();
        boolean wasOut = !isInCombat(sp);
        att.flags().setInCombatUntil(until);

        // Solo se sincroniza en el FLANCO. Marcar en combate pasa en cada golpe de una
        // ráfaga; mandar el attachment entero por cada uno sería un paquete por tick por
        // jugador, y el cliente solo necesita saber que el icono se enciende.
        if (wasOut) PlayerLifeCycle.syncIfServer(sp);
    }

    /** Marca a los dos lados de un intercambio. Null-safe: la mitad de las fuentes no son jugador. */
    public static void markBoth(Player attacker, Player victim) {
        if (attacker != null) mark(attacker);
        if (victim != null) mark(victim);
    }

    /** ¿Está en combate AHORA? Vale igual en cliente y servidor: compara con el gameTime local. */
    public static boolean isInCombat(Player p) {
        if (p == null) return false;
        PlayerStatsAttachment att = PlayerStatsAttachment.get(p);
        return p.level().getGameTime() < att.flags().getInCombatUntil();
    }

    /** Segundos que quedan, redondeados hacia arriba. Para el tooltip del icono del HUD. */
    public static int secondsLeft(Player p) {
        if (!isInCombat(p)) return 0;
        long left = PlayerStatsAttachment.get(p).flags().getInCombatUntil() - p.level().getGameTime();
        return (int) Math.ceil(left / 20.0);
    }

    /** Sale del estado de golpe. Lo llama el respawn: reaparecer no es seguir peleando. */
    public static void clear(ServerPlayer sp) {
        PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
        att.flags().setInCombatUntil(0L);
    }
}