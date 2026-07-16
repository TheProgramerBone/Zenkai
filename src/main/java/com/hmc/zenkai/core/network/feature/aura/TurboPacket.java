package com.hmc.zenkai.core.network.feature.aura;

import com.hmc.zenkai.Zenkai;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** C2S: petición de turbo on/off. El server valida (raza, energía) y confirma por sync. */
public record TurboPacket(boolean on) implements CustomPacketPayload {

    public static final Type<TurboPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "turbo"));

    public static final StreamCodec<FriendlyByteBuf, TurboPacket> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.BOOL, TurboPacket::on, TurboPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(TurboPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp) {
                TurboServerState.set(sp, pkt.on());
            }
        });
    }
}