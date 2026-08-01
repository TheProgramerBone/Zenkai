package com.hmc.zenkai.network;

import com.hmc.zenkai.Zenkai;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Servidor → cliente: abre el editor del marcador con los valores actuales. */
public record OpenNpcMarkerPayload(BlockPos pos, String npcType, float yaw,
                                   double offX, double offY, double offZ)
        implements CustomPacketPayload {

    public static final Type<OpenNpcMarkerPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "npc_marker_open"));

    public static final StreamCodec<FriendlyByteBuf, OpenNpcMarkerPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeBlockPos(p.pos());
                        buf.writeUtf(p.npcType());
                        buf.writeFloat(p.yaw());
                        buf.writeDouble(p.offX());
                        buf.writeDouble(p.offY());
                        buf.writeDouble(p.offZ());
                    },
                    buf -> new OpenNpcMarkerPayload(
                            buf.readBlockPos(), buf.readUtf(), buf.readFloat(),
                            buf.readDouble(), buf.readDouble(), buf.readDouble()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}