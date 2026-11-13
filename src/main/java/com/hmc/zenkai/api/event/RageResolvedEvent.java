package com.hmc.zenkai.api.event;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;
import org.jetbrains.annotations.Nullable;

/**
 * Se postea cuando el sistema de Rage Forms termina de evaluar un hecho para un jugador ({@code RageSystem.handle}),
 * con el resultado ya aplicado. Informativo, no cancelable. {@code trigger} es el nombre del detector en mayúsculas
 * ({@code ALLY_DEATH}, {@code PET_DEATH}, {@code ADDON}…), {@code intensity} el de la intensidad ({@code LOW},
 * {@code MEDIUM}, {@code HIGH}, {@code EXCEPTIONAL}) y {@code result} es {@code AWAKENING} (despertó una forma),
 * {@code PROGRESS} (sumó maestría) o {@code RECORDED} (solo quedó anotado: repetido, en enfriamiento o sin forma que
 * dar). Para saber QUÉ forma despertó, escucha {@link FormChangedEvent}.
 */
public final class RageResolvedEvent extends Event {
    private final ServerPlayer player;
    private final String trigger;
    private final String intensity;
    private final String result;
    @Nullable private final String eventId;

    public RageResolvedEvent(ServerPlayer player, String trigger, String intensity, String result, @Nullable String eventId) {
        this.player = player;
        this.trigger = trigger;
        this.intensity = intensity;
        this.result = result;
        this.eventId = eventId;
    }

    public ServerPlayer player() { return player; }
    public String trigger() { return trigger; }
    public String intensity() { return intensity; }
    public String result() { return result; }
    /** Id del hecho (una recompensa por id), o null si el llamante no dio ninguno. */
    @Nullable public String eventId() { return eventId; }
}
