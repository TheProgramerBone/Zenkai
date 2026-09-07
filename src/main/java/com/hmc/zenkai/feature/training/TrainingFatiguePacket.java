package com.hmc.zenkai.feature.training;

import com.hmc.zenkai.Zenkai;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * S2C: respuesta a {@link TrainingFatigueRequestPacket} — el multiplicador de eficiencia actual
 * (0..1, ver TrainingHooks.currentEfficiency), para que el panel "TP Modifiers" del hub lo
 * muestre y lo pliegue en "Effective TP bonus" junto a pesas/HTC. El handler vive en
 * ClientPayloadHandlers (nunca inline aquí), mismo patrón que TrainingSessionRewardPacket.
 */
public record TrainingFatiguePacket(double efficiency) implements CustomPacketPayload {

    public static final Type<TrainingFatiguePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "training_fatigue"));

    public static final StreamCodec<FriendlyByteBuf, TrainingFatiguePacket> STREAM_CODEC =
            StreamCodec.of((buf, pkt) -> buf.writeDouble(pkt.efficiency()),
                    buf -> new TrainingFatiguePacket(buf.readDouble()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
