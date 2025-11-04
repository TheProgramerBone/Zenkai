package com.hmc.db_renewed.network.wishes;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record WishImmortalPayload() implements CustomPacketPayload {
    public static final Type<WishImmortalPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("db_renewed","wish_immortal"));
    public static final StreamCodec<RegistryFriendlyByteBuf, WishImmortalPayload> STREAM_CODEC =
            StreamCodec.unit(new WishImmortalPayload());
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}