package com.hmc.db_renewed.network.wishes;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ConfirmVillagerWishPayload(ResourceLocation enchantmentId) implements CustomPacketPayload {
    public static final Type<ConfirmVillagerWishPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("db_renewed", "confirm_villager_wish"));

    public static final StreamCodec<?, ConfirmVillagerWishPayload> STREAM_CODEC =
            ResourceLocation.STREAM_CODEC.map(ConfirmVillagerWishPayload::new, ConfirmVillagerWishPayload::enchantmentId);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}