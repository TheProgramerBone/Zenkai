package com.hmc.zenkai.core.network.feature.combat;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.CombatModeClientState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * S2C: estado de BLOQUEO de otro jugador (por entityId), para que los demás clientes
 * reproduzcan su animación PAL de defensa. Se difunde desde KiCombatServer.setBlocking.
 */
public record BlockingSyncPacket(int entityId, boolean blocking) implements CustomPacketPayload {

    public static final Type<BlockingSyncPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "blocking_sync"));

    public static final StreamCodec<FriendlyByteBuf, BlockingSyncPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, pkt) -> {
                        buf.writeVarInt(pkt.entityId());
                        buf.writeBoolean(pkt.blocking());
                    },
                    buf -> new BlockingSyncPacket(buf.readVarInt(), buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(BlockingSyncPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() ->
                CombatModeClientState.onBlockingSync(pkt.entityId(), pkt.blocking()));
    }
}