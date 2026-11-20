package com.hmc.zenkai.feature.gravity;

import com.hmc.zenkai.Zenkai;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** S2C: la g de cámara de gravedad del jugador local (1 = fuera). Solo se manda al cambiar. */
public record GravityFieldSyncPacket(float g) implements CustomPacketPayload {

    public static final Type<GravityFieldSyncPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "gravity_field_sync"));

    public static final StreamCodec<FriendlyByteBuf, GravityFieldSyncPacket> STREAM_CODEC =
            StreamCodec.of((buf, pkt) -> buf.writeFloat(pkt.g()),
                    buf -> new GravityFieldSyncPacket(buf.readFloat()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(GravityFieldSyncPacket pkt, IPayloadContext ctx) {
        // Validado: una g por debajo de 1 o no finita no tiene sentido y se ignora.
        if (!Float.isFinite(pkt.g()) || pkt.g() < 1.0F) return;
        ctx.enqueueWork(() -> GravityFields.setClientLocalG(pkt.g()));
    }
}
