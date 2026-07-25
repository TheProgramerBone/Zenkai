package com.hmc.zenkai.feature.technique;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.KiChargeClientState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * S2C: "este jugador está cargando ESTA técnica". Va a los que le ven (trackers) y a él.
 * Lleva el ASPECTO, no una referencia al slot: los demás clientes no tienen las técnicas
 * ajenas, así que sin color/tamaño/tipo/posición no podrían dibujar nada. Es el mismo
 * criterio que el aura: lo derivado viaja resuelto.
 */
public record KiChargeStatePacket(int playerId, boolean charging, int rgb, int size,
                                  int typeOrdinal, int positionOrdinal)
        implements CustomPacketPayload {

    public static final Type<KiChargeStatePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "ki_charge_state"));

    public static final StreamCodec<FriendlyByteBuf, KiChargeStatePacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, pkt) -> {
                        buf.writeVarInt(pkt.playerId());
                        buf.writeBoolean(pkt.charging());
                        buf.writeInt(pkt.rgb());
                        buf.writeVarInt(pkt.size());
                        buf.writeVarInt(pkt.typeOrdinal());
                        buf.writeVarInt(pkt.positionOrdinal());
                    },
                    buf -> new KiChargeStatePacket(buf.readVarInt(), buf.readBoolean(),
                            buf.readInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(KiChargeStatePacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> KiChargeClientState.accept(pkt));
    }
}