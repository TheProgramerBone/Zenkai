package com.hmc.db_renewed.core.network.feature.wishes;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record OpenStackWishPayload() implements CustomPacketPayload {
    public static final Type<OpenStackWishPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("db_renewed", "open_stack_wish"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenStackWishPayload> STREAM_CODEC =
            StreamCodec.unit(new OpenStackWishPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}