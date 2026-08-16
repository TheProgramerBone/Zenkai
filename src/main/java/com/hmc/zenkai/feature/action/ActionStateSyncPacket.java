package com.hmc.zenkai.feature.action;

import com.hmc.zenkai.Zenkai;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * S2C: la acción exclusiva de un jugador. Se manda a los trackers Y al propio jugador
 * (patrón de TurboSyncPacket / BlockingSyncPacket), y SOLO cuando cambia.
 * startTick viaja para que el receptor derive el progreso sin recibir un contador por tick.
 * Es el mismo criterio que ya usaba KiChargeStatePacket.
 * El componente se llama typeOrdinal y no type A PROPÓSITO: un record con un componente
 * `type` genera un accesor type() que choca con CustomPacketPayload.type(). Mismo criterio
 * de nombres que KiChargeStatePacket (typeOrdinal / positionOrdinal).
 */
public record ActionStateSyncPacket(int entityId, int typeOrdinal, int phaseOrdinal,
                                    long startTick, int payload)
        implements CustomPacketPayload {

    public static final Type<ActionStateSyncPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "action_state"));

    public static final StreamCodec<FriendlyByteBuf, ActionStateSyncPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, pkt) -> {
                        buf.writeVarInt(pkt.entityId());
                        buf.writeByte(pkt.typeOrdinal());
                        buf.writeByte(pkt.phaseOrdinal());
                        buf.writeVarLong(pkt.startTick());
                        buf.writeVarInt(pkt.payload() + 1); // +1: el -1 no cabe en VarInt sin coste
                    },
                    buf -> new ActionStateSyncPacket(
                            buf.readVarInt(), buf.readByte(), buf.readByte(),
                            buf.readVarLong(), buf.readVarInt() - 1));

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static ActionStateSyncPacket of(int entityId, ActionState st) {
        return new ActionStateSyncPacket(entityId, st.type().ordinal(), st.phase().ordinal(),
                st.startTick(), st.payload());
    }

    public ActionState toState() {
        ActionType[] types = ActionType.values();
        ActionPhase[] phases = ActionPhase.values();
        ActionType t = (typeOrdinal >= 0 && typeOrdinal < types.length)
                ? types[typeOrdinal] : ActionType.NONE;
        ActionPhase p = (phaseOrdinal >= 0 && phaseOrdinal < phases.length)
                ? phases[phaseOrdinal] : ActionPhase.NONE;
        return new ActionState(t, p, startTick, payload);
    }

    public static void handle(ActionStateSyncPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() ->
                com.hmc.zenkai.client.action.ActionStateClient.accept(pkt.entityId(), pkt.toState()));
    }
}