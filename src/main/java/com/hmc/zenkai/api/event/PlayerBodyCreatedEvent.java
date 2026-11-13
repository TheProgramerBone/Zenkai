package com.hmc.zenkai.api.event;

import com.hmc.zenkai.content.entity.misc.PlayerBodyEntity;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;

/**
 * Se publica en el bus de NeoForge cuando nace el cuerpo de un jugador. Es el enganche para las rage
 * forms (#6): la mecánica de furia la define esa tarea, aquí solo se avisa. No se puede cancelar.
 *
 * @param permanent true si la muerte permanente estaba encendida (el desenlace es definitivo)
 */
public class PlayerBodyCreatedEvent extends Event {
    private final ServerPlayer owner;
    private final PlayerBodyEntity body;
    private final boolean permanent;

    public PlayerBodyCreatedEvent(ServerPlayer owner, PlayerBodyEntity body, boolean permanent) {
        this.owner = owner;
        this.body = body;
        this.permanent = permanent;
    }

    public ServerPlayer owner() { return owner; }
    public PlayerBodyEntity body() { return body; }
    public boolean permanent() { return permanent; }
}
