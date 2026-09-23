package com.hmc.zenkai.network;

import com.hmc.zenkai.Zenkai;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Servidor → cliente: abre el editor del conector de pieza con los valores actuales.
 *  {@code facing} viaja como el nombre serializado de la dirección (p.ej. "north") — es
 *  solo informativo en la pantalla, no editable desde ahí (ver PieceConnectorScreen). */
public record OpenPieceConnectorPayload(BlockPos pos, String socket, String facing)
        implements CustomPacketPayload {

    public static final Type<OpenPieceConnectorPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "piece_connector_open"));

    public static final StreamCodec<FriendlyByteBuf, OpenPieceConnectorPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeBlockPos(p.pos());
                        buf.writeUtf(p.socket());
                        buf.writeUtf(p.facing());
                    },
                    buf -> new OpenPieceConnectorPayload(
                            buf.readBlockPos(), buf.readUtf(), buf.readUtf()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
