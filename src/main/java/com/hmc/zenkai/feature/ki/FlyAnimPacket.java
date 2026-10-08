package com.hmc.zenkai.feature.ki;

import com.hmc.zenkai.Zenkai;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C2S: estado de la ANIMACIÓN de vuelo del jugador local (volando, boost).
 * Se envía SOLO al cambiar (edge-trigger en ClientFlyAnimState.sendIfChanged).
 *
 * Puramente cosmético: el servidor no toma decisiones de gameplay con esto (el boost real
 * de hitbox/velocidad va por FlyBoostPacket). Por eso la validación es mínima: solo
 * re-difusión a los jugadores que trackean a este.
 */
public record FlyAnimPacket(boolean flying, boolean boosting) implements CustomPacketPayload {

    public static final Type<FlyAnimPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "fly_anim"));

    public static final StreamCodec<FriendlyByteBuf, FlyAnimPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, pkt) -> {
                        buf.writeBoolean(pkt.flying());
                        buf.writeBoolean(pkt.boosting());
                    },
                    buf -> new FlyAnimPacket(buf.readBoolean(), buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(FlyAnimPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            FlyAnimServerState.update(sp, pkt.flying(), pkt.boosting());
        });
    }
}