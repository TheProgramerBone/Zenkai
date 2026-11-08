package com.hmc.zenkai.feature.rage;

import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.feature.race.RacePassives;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.NeoForge;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Detector de «supervivencia crítica» para las cinco razas: armar al bajar del 20 % de body (o al caer derribado) y disparar
 * {@link CriticalSurvivalEvent} al volver al 60 %. Usa los MISMOS umbrales que el zenkai del Saiyan
 * ({@link RacePassives#ZENKAI_TRIGGER}/{@link RacePassives#ZENKAI_RECOVER}) para que «sobrevivió al borde» signifique lo
 * mismo en todas partes, pero lleva su propio estado: el del zenkai se desarma al caer en su enfriamiento y no debe
 * esconderle este hecho a las rage forms (que ya tienen el suyo).
 * ESTADO EN MEMORIA, como el zenkai: un ciclo a medias no sobrevive a un reinicio (error barato). Lo avanza
 * RacePassiveSystem.tick, después de los gates: derribado no se evalúa, por eso el derribo arma por {@link #armDowned}.
 */
public final class CriticalSurvival {
    private CriticalSurvival() {}

    private static final class State {
        boolean armed;
        boolean wasDowned;
    }

    private static final Map<UUID, State> STATES = new HashMap<>();

    public static void forget(UUID id) { STATES.remove(id); }

    /** Cae derribado: arma el ciclo aunque el body de revivir (20 % redondeado) quede un pelo por encima del umbral. */
    public static void armDowned(ServerPlayer sp) {
        State s = STATES.computeIfAbsent(sp.getUUID(), k -> new State());
        s.armed = true;
        s.wasDowned = true;
    }

    public static void tick(Player p, PlayerStatsAttachment att) {
        if (!(p instanceof ServerPlayer sp) || att == null || !att.isRaceChosen()) return;
        // En el Otherworld el body a 0 se reinicia solo: no es sobrevivir a nada.
        if (att.isInOtherworld()) { STATES.remove(sp.getUUID()); return; }
        int max = att.getBodyMax();
        if (max <= 0) return;
        double frac = att.getBody() / (double) max;

        State s = STATES.get(sp.getUUID());
        if (frac <= RacePassives.ZENKAI_TRIGGER && att.getBody() > 0) {
            if (s == null) { s = new State(); STATES.put(sp.getUUID(), s); }
            s.armed = true;
            return;
        }
        if (s == null || !s.armed || frac < RacePassives.ZENKAI_RECOVER) return;

        boolean downed = s.wasDowned;
        STATES.remove(sp.getUUID());
        NeoForge.EVENT_BUS.post(new CriticalSurvivalEvent(sp, downed));
    }
}
