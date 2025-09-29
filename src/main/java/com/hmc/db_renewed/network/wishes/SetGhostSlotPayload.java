package com.hmc.db_renewed.network.wishes;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public record SetGhostSlotPayload(ItemStack chosen) implements CustomPacketPayload {
    public static final Type<SetGhostSlotPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("db_renewed", "set_ghost_slot"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetGhostSlotPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ItemStack.OPTIONAL_STREAM_CODEC, SetGhostSlotPayload::chosen,
                    SetGhostSlotPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}