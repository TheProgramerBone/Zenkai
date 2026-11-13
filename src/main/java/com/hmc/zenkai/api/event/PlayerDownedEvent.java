package com.hmc.zenkai.api.event;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;

import java.util.List;

/**
 * Un jugador acaba de caer DERRIBADO (body a 0, ventana de {@code DOWNED_TICKS}), todavía no ha muerto. Enganche de las rage
 * forms (aliado derribado cerca, rescate dentro de la ventana). No se publica en el Otherworld, donde no hay muerte que dar.
 * Se publica UNA vez por caída. No se puede cancelar.
 *
 * @param witnesses miembros de su party vivos, en su dimensión y a menos de {@code permadeath.witness_radius}; puede estar
 *                  vacía (sin party o radio 0): el evento se publica igual, porque el propio derribado también interesa
 * @param downedUntil {@code gameTime} en el que expira la ventana y llega la muerte real si nadie lo cura
 */
public class PlayerDownedEvent extends Event {
    private final ServerPlayer downed;
    private final List<ServerPlayer> witnesses;
    private final long downedUntil;

    public PlayerDownedEvent(ServerPlayer downed, List<ServerPlayer> witnesses, long downedUntil) {
        this.downed = downed;
        this.witnesses = List.copyOf(witnesses);
        this.downedUntil = downedUntil;
    }

    public ServerPlayer downed() { return downed; }
    public List<ServerPlayer> witnesses() { return witnesses; }
    public long downedUntil() { return downedUntil; }
}
