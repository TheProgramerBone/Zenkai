package com.hmc.zenkai.api.event;

import com.hmc.zenkai.feature.Race;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.Event;

/**
 * Se postea justo después de que un jugador fija su raza (elección inicial o respec) — ver
 * {@code ChooseRacePacket.handle}, tras {@code PlayerStatsAttachment#setRace}. Informativo, no
 * cancelable: para cuando llega aquí la elección ya pasó las comprobaciones previas (gamerule de
 * selección de raza).
 */
public final class RaceChosenEvent extends Event {
    private final Player player;
    private final Race race;

    public RaceChosenEvent(Player player, Race race) {
        this.player = player;
        this.race = race;
    }

    public Player player() { return player; }
    public Race race() { return race; }
}
