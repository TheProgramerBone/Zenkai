package com.hmc.zenkai.network;

import com.hmc.zenkai.Zenkai;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Cliente → servidor: guarda el socket y la cara editados en el conector de pieza.
 * {@code facing} viaja como el nombre serializado de la dirección (p.ej. "north") — lo pone
 * el botón "Girar" de PieceConnectorScreen, que solo cambia una variable local hasta que se
 * pulsa Guardar (un único viaje de ida y vuelta, nada intermedio).
 * El servidor NO confía en nada de aquí: valida permiso, distancia, chunk cargado, que el
 * bloque en pos siga siendo un piece_connector, y que socket/facing sean válidos antes de
 * aplicar (ver ModNetworking).
 */
public record SavePieceConnectorPayload(BlockPos pos, String socket, String facing) implements CustomPacketPayload {

    public static final Type<SavePieceConnectorPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "piece_connector_save"));

    public static final StreamCodec<FriendlyByteBuf, SavePieceConnectorPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeBlockPos(p.pos());
                        buf.writeUtf(p.socket(), 256);
                        buf.writeUtf(p.facing());
                    },
                    buf -> new SavePieceConnectorPayload(
                            buf.readBlockPos(), buf.readUtf(256), buf.readUtf()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
