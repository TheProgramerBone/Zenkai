package com.hmc.db_renewed.network.wishes;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record WishRevivePlayerPayload(String targetName) implements CustomPacketPayload {
    public static final Type<WishRevivePlayerPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("db_renewed","wish_revive_player"));

    public static final StreamCodec<RegistryFriendlyByteBuf, WishRevivePlayerPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, WishRevivePlayerPayload::targetName,
                    WishRevivePlayerPayload::new
            );
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
