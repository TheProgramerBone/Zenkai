package com.hmc.zenkai.api.event;

import com.hmc.zenkai.content.entity.misc.PlayerBodyEntity;
import com.hmc.zenkai.feature.party.PartyManager;
import com.hmc.zenkai.config.ServerConfig;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Alguien interactuó con un cuerpo. Enganche para las rage forms (#6): la furia puede dispararse al tocar el cuerpo de
 * un aliado o el propio de una vida anterior; aquí solo se avisa. No se puede cancelar (ya ocurrió).
 *
 * @param who       quién; null si lo hizo algo sin jugador detrás (ki de una entidad sin dueño)
 * @param own       el cuerpo es de {@code who}
 * @param ally      el dueño y {@code who} comparten party (false si es el propio)
 * @param permanent la muerte permanente estaba encendida: el desenlace es definitivo
 */
public class PlayerBodyInteractEvent extends Event {
    public enum Kind { OPEN, CARRY, BURY, DISINTEGRATE }

    private final ServerPlayer who;
    private final PlayerBodyEntity body;
    private final Kind kind;
    private final boolean own, ally, permanent;

    private PlayerBodyInteractEvent(@Nullable ServerPlayer who, PlayerBodyEntity body, Kind kind, boolean own, boolean ally) {
        this.who = who;
        this.body = body;
        this.kind = kind;
        this.own = own;
        this.ally = ally;
        this.permanent = ServerConfig.PERMADEATH_ENABLED.get();
    }

    @Nullable public ServerPlayer who() { return who; }
    public PlayerBodyEntity body() { return body; }
    public Kind kind() { return kind; }
    public boolean own() { return own; }
    public boolean ally() { return ally; }
    public boolean permanent() { return permanent; }

    /** Calcula si es propio o de un aliado y publica el evento. Solo servidor. */
    public static void fire(@Nullable ServerPlayer who, PlayerBodyEntity body, Kind kind) {
        UUID owner = body.owner().orElse(null);
        boolean own = who != null && owner != null && owner.equals(who.getUUID());
        boolean ally = false;
        if (who != null && owner != null && !own) {
            PartyManager.Party party = PartyManager.get(who.server).partyOf(owner);
            ally = party != null && party.members.contains(who.getUUID());
        }
        NeoForge.EVENT_BUS.post(new PlayerBodyInteractEvent(who, body, kind, own, ally));
    }
}
