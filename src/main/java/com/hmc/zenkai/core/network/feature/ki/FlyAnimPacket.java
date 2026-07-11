package com.hmc.zenkai.core.network.feature.ki;

import com.hmc.zenkai.Zenkai;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C2S: estado de la ANIMACIÓN de vuelo del jugador local (volando, dirección, boost).
 * Se envía SOLO al cambiar (edge-trigger en ClientFlyAnimState.sendIfChanged).
 *
 * Puramente cosmético: el servidor no toma decisiones de gameplay con esto (el boost real
 * de hitbox/velocidad va por FlyBoostPacket). Por eso la validación es mínima: clamp del
 * byte de dirección y re-difusión a los jugadores que trackean a este.
 *
 * dir = ordinal de ZenkaiPalAnimations.FlyDir (0..MAX_DIR). El servidor lo trata como byte
 * opaco: FlyDir es clase de CLIENTE y aquí no se referencia.
 */
public record FlyAnimPacket(boolean flying, byte dir, boolean boosting) implements CustomPacketPayload {

    /** FlyDir tiene 11 valores (IDLE..BACK_RIGHT). Mantener en sincronía si se añaden. */
    public static final byte MAX_DIR = 10;

    public static final Type<FlyAnimPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "fly_anim"));

    public static final StreamCodec<FriendlyByteBuf, FlyAnimPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, pkt) -> {
                        buf.writeBoolean(pkt.flying());
                        buf.writeByte(pkt.dir());
                        buf.writeBoolean(pkt.boosting());
                    },
                    buf -> new FlyAnimPacket(buf.readBoolean(), buf.readByte(), buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(FlyAnimPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            byte dir = (byte) Math.min(Math.max(pkt.dir(), 0), MAX_DIR);
            FlyAnimServerState.update(sp, pkt.flying(), dir, pkt.boosting());
        });
    }
}