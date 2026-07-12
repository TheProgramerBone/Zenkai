package com.hmc.zenkai.core.network.feature.combat;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.core.technique.KiCombatServer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C2S: empieza/termina la defensa (modo combate, manos vacías, click derecho sostenido).
 * Edge-triggered desde CombatModeClientState. El servidor revalida las manos.
 */
public record BlockingPacket(boolean blocking) implements CustomPacketPayload {

    public static final Type<BlockingPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "blocking"));

    public static final StreamCodec<FriendlyByteBuf, BlockingPacket> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.BOOL, BlockingPacket::blocking, BlockingPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(BlockingPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp) {
                KiCombatServer.setBlocking(sp, pkt.blocking());
            }
        });
    }
}