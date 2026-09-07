package com.hmc.zenkai.feature.training;

import com.hmc.zenkai.Zenkai;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * S2C: respuesta al fin de una sesión de minijuego de Training (Meditation/Ki Target Practice) —
 * el TP ENTERO que el servidor de verdad concedió (ya con fatiga/pesas/HTC aplicados por
 * TrainingHooks.grant()), para que la pantalla de resultado enseñe el número real en vez de que
 * el cliente intente adivinarlo. Se manda SIEMPRE tras procesar MeditationSessionPacket/
 * TargetPracticeSessionPacket, incluso con 0 TP (cooldown activo, sesión sin nada que contar) —
 * así la pantalla nunca se queda esperando una respuesta que no llega.
 * `record` es el récord persistido (TrainingData.bestMeditationTp/bestTargetPracticeTp) YA
 * actualizado con esta sesión si la superó — RESULTS lo usa para el aviso de "¡Nuevo récord!".
 * El handler vive en ClientPayloadHandlers (nunca inline aquí ni en ModNetworking): toca
 * Minecraft.getInstance().screen, una clase de cliente.
 */
public record TrainingSessionRewardPacket(int tpGranted, int record) implements CustomPacketPayload {

    public static final Type<TrainingSessionRewardPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "training_session_reward"));

    public static final StreamCodec<FriendlyByteBuf, TrainingSessionRewardPacket> STREAM_CODEC =
            StreamCodec.of((buf, pkt) -> {
                        buf.writeVarInt(pkt.tpGranted());
                        buf.writeVarInt(pkt.record());
                    },
                    buf -> new TrainingSessionRewardPacket(buf.readVarInt(), buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
