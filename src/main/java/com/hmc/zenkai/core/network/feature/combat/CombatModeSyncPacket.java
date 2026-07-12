package com.hmc.zenkai.core.network.feature.combat;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.CombatModeClientState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * S2C: estado del modo combate de OTRO jugador (por entityId), con su estilo (ordinal)
 * para elegir la pose PAL sin depender del attachment ajeno.
 */
public record CombatModeSyncPacket(int entityId, boolean active, byte styleOrdinal)
        implements CustomPacketPayload {

    public static final Type<CombatModeSyncPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "combat_mode_sync"));

    public static final StreamCodec<FriendlyByteBuf, CombatModeSyncPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, pkt) -> {
                        buf.writeVarInt(pkt.entityId());
                        buf.writeBoolean(pkt.active());
                        buf.writeByte(pkt.styleOrdinal());
                    },
                    buf -> new CombatModeSyncPacket(buf.readVarInt(), buf.readBoolean(), buf.readByte()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(CombatModeSyncPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() ->
                CombatModeClientState.onSync(pkt.entityId(), pkt.active(), pkt.styleOrdinal()));
    }
}