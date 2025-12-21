package com.hmc.db_renewed.core.network.feature.wishes;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public record OpenWishScreenPayload() implements CustomPacketPayload {
    public static final Type<OpenWishScreenPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("db_renewed", "open_wish_screen"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenWishScreenPayload> STREAM_CODEC =
            StreamCodec.of((buf, payload) -> {
            },buf -> new OpenWishScreenPayload());

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}