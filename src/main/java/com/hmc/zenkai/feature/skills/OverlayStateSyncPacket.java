package com.hmc.zenkai.feature.skills;

import com.hmc.zenkai.Zenkai;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * S2C: snapshot completo de los {@link OverlayStateDef} (login y /reload). El cliente lo
 * necesita para resolver el aura de un jugador remoto con un overlay activo — igual motivo que
 * {@code FormSyncPacket}.
 */
public record OverlayStateSyncPacket(List<OverlayStateDef> states) implements CustomPacketPayload {

    public static final Type<OverlayStateSyncPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "overlay_state_sync"));

    public static final StreamCodec<FriendlyByteBuf, OverlayStateSyncPacket> STREAM_CODEC =
            StreamCodec.composite(
                    OverlayStateDef.STREAM_CODEC.apply(ByteBufCodecs.list()), OverlayStateSyncPacket::states,
                    OverlayStateSyncPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(OverlayStateSyncPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Map<String, OverlayStateDef> map = new LinkedHashMap<>();
            for (OverlayStateDef d : pkt.states()) map.put(d.id(), d);
            OverlayStateDef.replaceAll(map);
        });
    }
}
