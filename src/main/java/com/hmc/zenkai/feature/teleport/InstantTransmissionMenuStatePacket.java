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
 * S2C, self-only: qué destinos ha descubierto el jugador, qué dimensiones ha visitado alguna
 * vez, y qué waypoints (ver InstantTransmissionAttachment) existen ya — lo que
 * InstantTransmissionMenuScreen necesita para pintar las filas del menú. El attachment del
 * servidor NUNCA llega solo por `getData(...)` en el cliente (no es un attachment
 * auto-sincronizado como PlayerStatsAttachment); sin este packet la pantalla vería siempre los
 * conjuntos vacíos y todo aparecería bloqueado/oculto pase lo que pase.
 * Se manda en el login (InstantTransmissionMenuSync) y cada vez que el servidor descubre algo
 * nuevo (TeleportDiscoverySystem, EndOuterIslandTracker) — nunca en bucle, solo en los cambios
 * reales.
 */
public record InstantTransmissionMenuStatePacket(
        List<String> discoveredIds, List<String> visitedDimensionIds, List<String> waypointIds)
        implements CustomPacketPayload {

    public static final Type<InstantTransmissionMenuStatePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "instant_transmission_menu_state"));

    public static final StreamCodec<FriendlyByteBuf, InstantTransmissionMenuStatePacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()),
                    InstantTransmissionMenuStatePacket::discoveredIds,
                    ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()),
                    InstantTransmissionMenuStatePacket::visitedDimensionIds,
                    ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()),
                    InstantTransmissionMenuStatePacket::waypointIds,
                    InstantTransmissionMenuStatePacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(InstantTransmissionMenuStatePacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> InstantTransmissionClientState.applyMenuState(
                pkt.discoveredIds(), pkt.visitedDimensionIds(), pkt.waypointIds()));
    }
}
