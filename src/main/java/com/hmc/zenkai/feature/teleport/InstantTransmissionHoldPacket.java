package com.hmc.zenkai.feature.teleport;

import com.hmc.zenkai.Zenkai;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C2S. Manda solo el FLANCO de pulsar/soltar TAB, no cada tick (mismo criterio que
 * TransformHoldPacket con la tecla de transformar). Toda la lógica real (resolver el blink,
 * contar "quieto", armar el menú) vive en InstantTransmissionSystem; este handler solo refleja
 * la intención del cliente en el attachment.
 */
public record InstantTransmissionHoldPacket(boolean holding) implements CustomPacketPayload {

    public static final Type<InstantTransmissionHoldPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "instant_transmission_hold"));

    public static final StreamCodec<FriendlyByteBuf, InstantTransmissionHoldPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, pkt) -> buf.writeBoolean(pkt.holding()),
                    buf -> new InstantTransmissionHoldPacket(buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(InstantTransmissionHoldPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            InstantTransmissionAttachment.get(sp).setHolding(pkt.holding());
        });
    }
}
