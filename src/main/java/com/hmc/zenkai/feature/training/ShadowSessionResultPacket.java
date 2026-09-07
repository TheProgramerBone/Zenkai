package com.hmc.zenkai.feature.training;

import com.hmc.zenkai.Zenkai;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * S2C: resumen de una pelea de "Train with your shadow" al morir el clon —
 * {@link ShadowTrainingManager#onShadowDeath} calcula `earned` como la diferencia de TP entre el
 * snapshot tomado al arrancar la sesión (TrainingData.shadowSessionStartTp) y el TP actual, la
 * compara contra el récord persistido y manda ambos aquí. Shadow no tiene pantalla propia
 * durante la pelea (ocurre en el mundo con el HUD normal) — este packet es lo que dispara el
 * popup de resumen (ver ClientPayloadHandlers/ShadowResultScreen), mismo idioma visual que
 * RESULTS de Meditation/Ki Target Practice.
 */
public record ShadowSessionResultPacket(int earnedTp, int record) implements CustomPacketPayload {

    public static final Type<ShadowSessionResultPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "shadow_session_result"));

    public static final StreamCodec<FriendlyByteBuf, ShadowSessionResultPacket> STREAM_CODEC =
            StreamCodec.of((buf, pkt) -> {
                        buf.writeVarInt(pkt.earnedTp());
                        buf.writeVarInt(pkt.record());
                    },
                    buf -> new ShadowSessionResultPacket(buf.readVarInt(), buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
