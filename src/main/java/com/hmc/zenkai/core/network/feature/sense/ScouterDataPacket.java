package com.hmc.zenkai.core.network.feature.sense;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.ScouterClientState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** S2C: resultado del scan del scouter — si hay objetivo en la mira y su PL. */
public record ScouterDataPacket(boolean found, long powerLevel) implements CustomPacketPayload {

    public static final Type<ScouterDataPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "scouter_data"));

    public static final StreamCodec<FriendlyByteBuf, ScouterDataPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, pkt) -> { buf.writeBoolean(pkt.found()); buf.writeLong(pkt.powerLevel()); },
                    buf -> new ScouterDataPacket(buf.readBoolean(), buf.readLong()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(ScouterDataPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ScouterClientState.onData(pkt.found(), pkt.powerLevel()));
    }
}