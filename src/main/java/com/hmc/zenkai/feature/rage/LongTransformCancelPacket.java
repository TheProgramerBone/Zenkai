package com.hmc.zenkai.feature.rage;

import com.hmc.zenkai.Zenkai;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/**
 * S2C: este jugador soltó la tecla de transformar (o la carga se cortó) ANTES de completarse, así que la secuencia larga que se
 * avisó al empezar debe cancelarse: pose, aura, viñeta, cámara y retraso de la forma. Es solo presentación; el estado ya lo cortó
 * el servidor.
 */
public record LongTransformCancelPacket(UUID player) implements CustomPacketPayload {

    public static final Type<LongTransformCancelPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "long_transform_cancel"));

    public static final StreamCodec<FriendlyByteBuf, LongTransformCancelPacket> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> buf.writeUUID(p.player()),
            buf -> new LongTransformCancelPacket(buf.readUUID()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(LongTransformCancelPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> com.hmc.zenkai.client.rage.LongTransformCancel.apply(pkt.player()));
    }
}
