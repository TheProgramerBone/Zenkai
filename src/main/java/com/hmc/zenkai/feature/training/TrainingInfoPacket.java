package com.hmc.zenkai.feature.training;

import com.hmc.zenkai.Zenkai;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * S2C: respuesta a {@link TrainingInfoRequestPacket} — récord persistido y TP potencial de la
 * sesión, para que INTRO los muestre antes de jugar. `potentialTp` es -1 para SHADOW (sin techo
 * de sesión discreto, ver el handler de la request) — el cliente debe ocultar esa línea en vez
 * de enseñar "-1". El handler vive en ClientPayloadHandlers (nunca inline aquí), mismo patrón
 * que TrainingSessionRewardPacket.
 */
public record TrainingInfoPacket(int minigame, int record, int potentialTp) implements CustomPacketPayload {

    public static final Type<TrainingInfoPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "training_info"));

    public static final StreamCodec<FriendlyByteBuf, TrainingInfoPacket> STREAM_CODEC =
            StreamCodec.of((buf, pkt) -> {
                        buf.writeVarInt(pkt.minigame());
                        buf.writeVarInt(pkt.record());
                        buf.writeVarInt(pkt.potentialTp() + 1); // +1: writeVarInt no admite -1
                    },
                    buf -> new TrainingInfoPacket(buf.readVarInt(), buf.readVarInt(), buf.readVarInt() - 1));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
