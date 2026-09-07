package com.hmc.zenkai.feature.training;

import com.hmc.zenkai.Zenkai;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C2S: "dame mi eficiencia de entrenamiento actual" — disparado por TrainingHubScreen al abrir
 * el hub, para poder mostrar la fatiga en el panel "TP Modifiers" (pedido explícito del usuario;
 * antes quedaba fuera a propósito por falta de este packet, ver el javadoc viejo de
 * TrainingHubScreen). Sin payload: no hace falta decir qué minijuego, es un dato del jugador,
 * no de una sesión. Responde con {@link TrainingFatiguePacket}.
 */
public record TrainingFatigueRequestPacket() implements CustomPacketPayload {

    public static final Type<TrainingFatigueRequestPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "training_fatigue_request"));

    public static final StreamCodec<FriendlyByteBuf, TrainingFatigueRequestPacket> STREAM_CODEC =
            StreamCodec.unit(new TrainingFatigueRequestPacket());

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(TrainingFatigueRequestPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            PacketDistributor.sendToPlayer(sp,
                    new TrainingFatiguePacket(TrainingHooks.currentEfficiency(sp)));
        });
    }
}
