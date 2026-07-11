package com.hmc.zenkai.core.network.feature.sense;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.ScouterClientState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * S2C: resultado del escaneo por área del scouter (modos STRONGEST y RADAR).
 * El cliente cachea la POSICIÓN y recalcula la flecha (yaw relativo) cada frame,
 * así la flecha responde al giro de cámara aunque el scan sea cada 20 ticks.
 */
public record ScouterAreaDataPacket(byte mode, byte status, double x, double y, double z, long powerLevel)
        implements CustomPacketPayload {

    public static final byte STATUS_NONE = 0;        // sin señal
    public static final byte STATUS_FOUND = 1;       // objetivo/esfera encontrada
    public static final byte STATUS_UNAVAILABLE = 2; // radar sin la mejora

    public static final Type<ScouterAreaDataPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "scouter_area_data"));

    public static final StreamCodec<FriendlyByteBuf, ScouterAreaDataPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, pkt) -> {
                        buf.writeByte(pkt.mode());
                        buf.writeByte(pkt.status());
                        buf.writeDouble(pkt.x());
                        buf.writeDouble(pkt.y());
                        buf.writeDouble(pkt.z());
                        buf.writeLong(pkt.powerLevel());
                    },
                    buf -> new ScouterAreaDataPacket(buf.readByte(), buf.readByte(),
                            buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readLong()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(ScouterAreaDataPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ScouterClientState.onAreaData(
                pkt.mode(), pkt.status(), pkt.x(), pkt.y(), pkt.z(), pkt.powerLevel()));
    }
}