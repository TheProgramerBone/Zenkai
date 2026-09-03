package com.hmc.zenkai.feature.teleport;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.InstantTransmissionClientState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/**
 * S2C: la lista de dimensiones bloqueadas para Transmisión Instantánea (ver
 * InstantTransmissionBlocklistManager/InstantTransmissionBlocklist), para que
 * InstantTransmissionMenuScreen pueda ocultar esas filas sin necesitar ida y vuelta al
 * servidor. Se manda en el login y cada /reload (mismo patrón que AuraSignatureSyncPacket) —
 * NUNCA en bucle, solo cuando el datapack cambia de verdad.
 */
public record InstantTransmissionBlocklistSyncPacket(List<String> blockedDimensions)
        implements CustomPacketPayload {

    public static final Type<InstantTransmissionBlocklistSyncPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "instant_transmission_blocklist_sync"));

    public static final StreamCodec<FriendlyByteBuf, InstantTransmissionBlocklistSyncPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()),
                    InstantTransmissionBlocklistSyncPacket::blockedDimensions,
                    InstantTransmissionBlocklistSyncPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(InstantTransmissionBlocklistSyncPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> InstantTransmissionClientState.applyBlocklist(pkt.blockedDimensions()));
    }
}
