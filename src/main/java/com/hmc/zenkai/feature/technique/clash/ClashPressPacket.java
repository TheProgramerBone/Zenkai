package com.hmc.zenkai.feature.technique.clash;

import com.hmc.zenkai.Zenkai;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * C2S: «pulsé». NO lleva el valor de la barra: lleva el tick de juego del CLIENTE en que se
 * pulsó, y solo para compensar la latencia. El servidor lo acepta únicamente hacia atrás, con un
 * tope (clash.ping_grace_ticks) y nunca por encima del ping real medido del jugador
 * (ClashManager.press), de modo que un cliente modificado no puede elegir su puntuación: a lo
 * sumo retrasa su pulsación lo que su conexión ya justifica.
 * Lleva el id del choque para ignorar una pulsación tardía de uno que ya terminó.
 */
public record ClashPressPacket(int clashId, long clientTick) implements CustomPacketPayload {

    public static final Type<ClashPressPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "clash_press"));

    public static final StreamCodec<FriendlyByteBuf, ClashPressPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeVarInt(p.clashId());
                        buf.writeVarLong(p.clientTick());
                    },
                    buf -> new ClashPressPacket(buf.readVarInt(), buf.readVarLong()));

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(ClashPressPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            ClashManager.press(sp, pkt.clashId(), pkt.clientTick());
        });
    }
}
