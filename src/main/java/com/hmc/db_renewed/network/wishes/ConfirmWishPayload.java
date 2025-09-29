package com.hmc.db_renewed.network.wishes;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ConfirmWishPayload() implements CustomPacketPayload {
    public static final Type<ConfirmWishPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("db_renewed", "confirm_wish"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ConfirmWishPayload> STREAM_CODEC =
            StreamCodec.unit(new ConfirmWishPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}