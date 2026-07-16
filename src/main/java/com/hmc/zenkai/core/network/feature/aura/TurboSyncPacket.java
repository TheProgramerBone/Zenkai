package com.hmc.zenkai.core.network.feature.aura;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.AuraClientState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/** S2C: estado de turbo de un jugador (confirmación al propio y broadcast a trackers). */
public record TurboSyncPacket(int entityId, boolean on) implements CustomPacketPayload {

    public static final Type<TurboSyncPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "turbo_sync"));

    public static final StreamCodec<FriendlyByteBuf, TurboSyncPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, TurboSyncPacket::entityId,
                    ByteBufCodecs.BOOL, TurboSyncPacket::on,
                    TurboSyncPacket::new);

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(TurboSyncPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> AuraClientState.applySync(pkt.entityId(), pkt.on()));
    }
}