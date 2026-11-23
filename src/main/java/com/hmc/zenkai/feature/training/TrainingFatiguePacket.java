package com.hmc.zenkai.feature.training;

import com.hmc.zenkai.Zenkai;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * S2C: respuesta a {@link TrainingFatigueRequestPacket} — el multiplicador de eficiencia actual
 * (0..1, ver TrainingHooks.currentEfficiency) de CADA categoría (ver TrainingCategory), para que
 * el panel "TP Modifiers" del hub muestre hasta 3 filas de fatiga independientes en vez de una
 * sola compartida (2026-09-09, pedido explícito del usuario — las fatigas ya NO se comparten
 * entre categorías; desde 2026-11-22 cuatro, con la de los drones de la cámara de gravedad). Desde 2026-11-01 lleva también el RÉCORD de los tres minijuegos, para las
 * tarjetas del hub (viaja aquí y no en tres TrainingInfoRequestPacket: el hub ya pide este mismo
 * packet al abrirse, así que no suma ningún viaje). El handler vive en ClientPayloadHandlers (nunca inline aquí), mismo patrón
 * que TrainingSessionRewardPacket.
 */
public record TrainingFatiguePacket(double combatEfficiency, double meditationEfficiency,
                                    double targetPracticeEfficiency, double gravityEfficiency,
                                    int shadowRecord, int meditationRecord, int targetPracticeRecord)
        implements CustomPacketPayload {

    public static final Type<TrainingFatiguePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "training_fatigue"));

    public static final StreamCodec<FriendlyByteBuf, TrainingFatiguePacket> STREAM_CODEC =
            StreamCodec.of((buf, pkt) -> {
                        buf.writeDouble(pkt.combatEfficiency());
                        buf.writeDouble(pkt.meditationEfficiency());
                        buf.writeDouble(pkt.targetPracticeEfficiency());
                        buf.writeDouble(pkt.gravityEfficiency());
                        buf.writeVarInt(pkt.shadowRecord());
                        buf.writeVarInt(pkt.meditationRecord());
                        buf.writeVarInt(pkt.targetPracticeRecord());
                    },
                    buf -> new TrainingFatiguePacket(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
                            buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
