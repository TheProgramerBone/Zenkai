package com.hmc.zenkai.feature.action;

import com.hmc.zenkai.Zenkai;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * S2C, SOLO al jugador que lo pidió: "esa acción no salió, y por esto".
 *
 * Existe para la reconciliación de la predicción: el cliente arranca cooldown y animación de
 * forma optimista, y hasta ahora un rechazo del servidor dejaba el cooldown local corriendo
 * en silencio. Con las guardas compartidas de ActionRules los rechazos deberían ser raros —
 * este paquete es la red para los que queden (latencia, estado que cambió entre medias).
 *
 * payload identifica QUÉ se rechazó: ordinal de PhysicalTechnique o slot de técnica de ki.
 */
public record ActionRejectPacket(int typeOrdinal, int reasonOrdinal, int payload)
        implements CustomPacketPayload {

    public static final Type<ActionRejectPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "action_reject"));

    public static final StreamCodec<FriendlyByteBuf, ActionRejectPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, pkt) -> {
                        buf.writeByte(pkt.typeOrdinal());
                        buf.writeByte(pkt.reasonOrdinal());
                        buf.writeVarInt(pkt.payload() + 1);
                    },
                    buf -> new ActionRejectPacket(
                            buf.readByte(), buf.readByte(), buf.readVarInt() - 1));

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static ActionRejectPacket of(ActionType type, ActionReject reason, int payload) {
        return new ActionRejectPacket(type.ordinal(), reason.ordinal(), payload);
    }

    public ActionType actionType() {
        ActionType[] v = ActionType.values();
        return (typeOrdinal >= 0 && typeOrdinal < v.length) ? v[typeOrdinal] : ActionType.NONE;
    }

    public ActionReject reason() {
        ActionReject[] v = ActionReject.values();
        return (reasonOrdinal >= 0 && reasonOrdinal < v.length) ? v[reasonOrdinal] : ActionReject.OK;
    }

    public static void handle(ActionRejectPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> com.hmc.zenkai.client.CombatModeClientState.onRejected(
                pkt.actionType(), pkt.reason(), pkt.payload()));
    }
}