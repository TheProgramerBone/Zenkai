package com.hmc.zenkai.api.event;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

/**
 * Se postea cuando el alineamiento de un jugador va a cambiar por una acción del juego ({@code AlignmentManager.change}),
 * ANTES de aplicarlo. Mutable ({@link #setDelta}) y cancelable (no cambia nada). {@code reason} es {@code kill} (matar a
 * algo con valor en {@code zenkai_alignment}), {@code bury} (enterrar el cuerpo de otro jugador) o el que ponga un
 * addon que llame a {@code AlignmentManager.change}. NO se postea al fijar el valor a mano (comandos, reencarnación) ni
 * bajo la marca Majin, que clava el alineamiento en -100. El resultado final se satura a [-100, 100].
 */
public final class AlignmentChangeEvent extends Event implements ICancellableEvent {
    private final ServerPlayer player;
    private final String reason;
    private int delta;

    public AlignmentChangeEvent(ServerPlayer player, int delta, String reason) {
        this.player = player;
        this.delta = delta;
        this.reason = reason;
    }

    public ServerPlayer player() { return player; }
    public String reason() { return reason; }
    public int delta() { return delta; }
    public void setDelta(int delta) { this.delta = delta; }
}
