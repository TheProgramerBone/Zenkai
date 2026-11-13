package com.hmc.zenkai.api.event;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;

/**
 * Se postea cuando un jugador RECIBE God Ki (la prueba de un maestro, el ritual, o una concesión de addon vía
 * {@code GodKi.grantFromAddon}), justo después de concederlo. Informativo, no cancelable: God Ki ya está en su
 * poder. {@code via} es el nombre de {@code SkillTree.Via} en minúsculas: {@code master_trial}, {@code ritual},
 * {@code event}… Sirve para dar recompensas, logros o lanzar una escena propia.
 */
public final class GodKiGrantedEvent extends Event {
    private final ServerPlayer player;
    private final String via;

    public GodKiGrantedEvent(ServerPlayer player, String via) {
        this.player = player;
        this.via = via;
    }

    public ServerPlayer player() { return player; }
    public String via() { return via; }
}
