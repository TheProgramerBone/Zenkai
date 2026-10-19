package com.hmc.zenkai.feature.combat;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.feature.technique.PhysicalCombatServer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C2S: el jugador agarrado pulsó clic o espacio durante un skill check. Solo viaja la INTENCIÓN: qué check era y
 * cuántos ticks llevaba la aguja desde su punto de vista. El servidor decide el acierto con su propio reloj
 * (PhysicalCombatServer.onEscapePress acota ese valor al ping razonable), así que mandar un tiempo falso solo
 * mueve la aguja unos ticks, no regala la zona.
 */
public record GrabEscapePressPacket(int checkIndex, float elapsedTicks) implements CustomPacketPayload {

    public static final Type<GrabEscapePressPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "grab_escape_press"));

    public static final StreamCodec<FriendlyByteBuf, GrabEscapePressPacket> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.checkIndex());
                buf.writeFloat(p.elapsedTicks());
            },
            buf -> new GrabEscapePressPacket(buf.readVarInt(), buf.readFloat()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(GrabEscapePressPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp) {
                PhysicalCombatServer.onEscapePress(sp, pkt.checkIndex(), pkt.elapsedTicks());
            }
        });
    }
}
