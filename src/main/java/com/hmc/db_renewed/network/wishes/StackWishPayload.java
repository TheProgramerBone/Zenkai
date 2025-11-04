package com.hmc.db_renewed.network.wishes;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record StackWishPayload() implements CustomPacketPayload {
    public static final Type<StackWishPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("db_renewed", "confirm_wish"));

    public static final StreamCodec<RegistryFriendlyByteBuf, StackWishPayload> STREAM_CODEC =
            StreamCodec.unit(new StackWishPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}