package com.hmc.zenkai.api.event;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

/**
 * Se postea cuando un jugador compra algo EN PERSONA ante un maestro (nivel 1 de una habilidad
 * con maestro vía {@code SkillBuyPacket}, o una técnica firma vía {@code TechniquePacket}/
 * {@code PhysicalTechniquePacket} en su rama UNLOCK) — justo tras pasar
 * {@code MasterManager.check} y antes de cobrar el TP. Cancelable: un addon puede vetar una
 * compra concreta ante un maestro suyo (p. ej. un requisito adicional que el propio
 * {@code MasterDef} no modela).
 */
public final class MasterPurchaseEvent extends Event implements ICancellableEvent {
    private final ServerPlayer player;
    private final String masterId;
    private final String itemId;

    public MasterPurchaseEvent(ServerPlayer player, String masterId, String itemId) {
        this.player = player;
        this.masterId = masterId;
        this.itemId = itemId;
    }

    public ServerPlayer player() { return player; }
    public String masterId() { return masterId; }
    public String itemId() { return itemId; }
}
