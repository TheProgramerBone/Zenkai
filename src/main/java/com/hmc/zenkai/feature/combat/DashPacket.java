package com.hmc.zenkai.feature.combat;

import com.hmc.zenkai.Zenkai;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C2S: el jugador pulsó DOS VECES una tecla de movimiento en modo combate y pide un dash en esa
 * dirección. Solo viaja la dirección (0 = adelante, 1 = atrás, 2 = izquierda, 3 = derecha,
 * relativa a hacia dónde mira el cuerpo): la velocidad, el cooldown y las guardas salen
 * SIEMPRE del servidor (DashServer), nunca del packet.
 */
public record DashPacket(int dir) implements CustomPacketPayload {

    public static final Type<DashPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "dash"));

    public static final StreamCodec<FriendlyByteBuf, DashPacket> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, DashPacket::dir, DashPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(DashPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            DashServer.tryDash(sp, pkt.dir());
        });
    }
}
