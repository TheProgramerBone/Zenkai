package com.hmc.zenkai.feature.combat;

import com.hmc.zenkai.Zenkai;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C2S: el jugador pulsó la tecla de parry. No lleva datos: el servidor decide si puede abrir la
 * ventana (modo combate, sin otra acción en curso, fuera de cooldown) en ParryServer.tryActivate.
 */
public record ParryPacket() implements CustomPacketPayload {

    public static final Type<ParryPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "parry"));

    public static final StreamCodec<FriendlyByteBuf, ParryPacket> STREAM_CODEC =
            StreamCodec.unit(new ParryPacket());

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(ParryPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            ParryServer.tryActivate(sp);
        });
    }
}
