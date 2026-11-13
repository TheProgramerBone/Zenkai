package com.hmc.zenkai.api.event;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

/**
 * Se postea cuando un jugador intenta comprar un nodo del skill tree de transformaciones
 * ({@code SkillTreeBuyPacket}), justo DESPUÉS de que el servidor confirme que el nodo es comprable
 * (requisitos, TP, MIND, ritual) y ANTES de cobrar nada. Cancelable: la compra se rechaza sin coste
 * y sin cambio alguno. Sirve para añadir un requisito propio a un nodo que el datapack no modela
 * (p. ej. una quest de un addon) o para reaccionar a una compra concreta. Los nodos de skill con
 * maestro pasan además por {@link MasterPurchaseEvent}.
 */
public final class SkillTreeNodePurchaseEvent extends Event implements ICancellableEvent {
    private final ServerPlayer player;
    private final ResourceLocation nodeId;

    public SkillTreeNodePurchaseEvent(ServerPlayer player, ResourceLocation nodeId) {
        this.player = player;
        this.nodeId = nodeId;
    }

    public ServerPlayer player() { return player; }
    /** Id del nodo, p. ej. {@code zenkai:ssj_god} o {@code zenkai:node/race_saiyan}. */
    public ResourceLocation nodeId() { return nodeId; }
}
