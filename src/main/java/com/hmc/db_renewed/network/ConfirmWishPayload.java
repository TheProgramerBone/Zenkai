package com.hmc.db_renewed.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public record ConfirmWishPayload(ItemStack chosen) implements CustomPacketPayload {
    public static final Type<ConfirmWishPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("db_renewed", "confirm_wish"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ConfirmWishPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ItemStack.OPTIONAL_STREAM_CODEC, ConfirmWishPayload::chosen,
                    ConfirmWishPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}