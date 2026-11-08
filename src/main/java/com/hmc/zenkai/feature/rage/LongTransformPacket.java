package com.hmc.zenkai.feature.rage;

import com.hmc.zenkai.Zenkai;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/**
 * S2C: este jugador va a transformarse en {@code form} POR PRIMERA VEZ, así que su aura debe reproducir la secuencia LARGA y no la
 * corta. Lo manda {@link FirstTransformations} al propio jugador y a quienes lo ven, ANTES de que el cambio de forma llegue por su
 * sync normal: el cliente apunta el aviso ({@link LongTransformState}) y el compositor de auras lo consume cuando detecta el cambio.
 * Es solo presentación: no concede ni cambia nada, y que el cliente la salte o la reduzca (preferencia) no afecta al servidor.
 */
public record LongTransformPacket(UUID player, ResourceLocation form) implements CustomPacketPayload {

    public static final Type<LongTransformPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "long_transform"));

    public static final StreamCodec<FriendlyByteBuf, LongTransformPacket> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> { buf.writeUUID(p.player()); buf.writeResourceLocation(p.form()); },
            buf -> new LongTransformPacket(buf.readUUID(), buf.readResourceLocation()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(LongTransformPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> LongTransformState.mark(pkt.player()));
    }
}
