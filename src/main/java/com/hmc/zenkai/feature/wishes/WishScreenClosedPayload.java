package com.hmc.zenkai.feature.wishes;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C2S: el jugador dejó las pantallas de deseo sin pedir nada (Escape, «atrás»…). Libera a Shenlong para que otro
 * jugador pueda hablarle (ver {@link WishSessions}, exclusividad). No concede nada, así que un cliente que NO lo
 * mande solo se perjudica a sí mismo: su sesión caduca igualmente por tiempo y por distancia.
 */
public record WishScreenClosedPayload() implements CustomPacketPayload {
    public static final Type<WishScreenClosedPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("zenkai", "wish_screen_closed"));
    public static final StreamCodec<RegistryFriendlyByteBuf, WishScreenClosedPayload> STREAM_CODEC =
            StreamCodec.unit(new WishScreenClosedPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(WishScreenClosedPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp) WishSessions.close(sp);
        });
    }
}
