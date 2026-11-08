package com.hmc.zenkai.feature.player.death;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;

import java.util.List;

/**
 * Murió un jugador y su party lo presenció: {@code witnesses} son los miembros de su party vivos, en su misma dimensión y a
 * menos de {@code permadeath.witness_radius} bloques. Enganche para las rage forms (#6), que se disparan al ver caer a un
 * aliado; la mecánica de furia la define esa tarea. Solo se publica si hay al menos un testigo. No se puede cancelar.
 *
 * @param permanent la muerte permanente estaba encendida: el desenlace es definitivo
 */
public class PartyMemberDiedEvent extends Event {
    private final ServerPlayer dead;
    private final List<ServerPlayer> witnesses;
    private final boolean permanent;

    public PartyMemberDiedEvent(ServerPlayer dead, List<ServerPlayer> witnesses, boolean permanent) {
        this.dead = dead;
        this.witnesses = List.copyOf(witnesses);
        this.permanent = permanent;
    }

    public ServerPlayer dead() { return dead; }
    public List<ServerPlayer> witnesses() { return witnesses; }
    public boolean permanent() { return permanent; }
}
