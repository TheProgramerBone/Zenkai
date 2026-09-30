package com.hmc.zenkai.api.event;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

/**
 * Se postea desde {@code PlayerFormAttachment#setFormId(Player, ResourceLocation)} — el único
 * punto que de verdad cambia la transformación activa de un jugador (ritual de Oozaru, escalera
 * de formas normal, Potential Unlock, o el deslizamiento automático entre hermanas divinas como
 * SSJ Blue/Rosé) — justo ANTES de aplicar el cambio. Cancelable: un addon puede vetar una
 * transformación concreta (p. ej. un efecto de "corrupción de ki" que bloquee transformarse
 * mientras esté activo). Cancelarlo deja al jugador en {@link #from()}.
 *
 * No se postea en los caminos silenciosos de inicialización/reset/carga de NBT (esos escriben el
 * campo directamente, sin pasar por el setter) — solo representa cambios de forma "de verdad"
 * ocurridos durante el juego.
 */
public final class FormChangedEvent extends Event implements ICancellableEvent {
    private final Player player;
    private final ResourceLocation from;
    private final ResourceLocation to;

    public FormChangedEvent(Player player, ResourceLocation from, ResourceLocation to) {
        this.player = player;
        this.from = from;
        this.to = to;
    }

    public Player player() { return player; }
    public ResourceLocation from() { return from; }
    public ResourceLocation to() { return to; }
}
