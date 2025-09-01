package com.hmc.db_renewed.network;

import com.hmc.db_renewed.gui.StackWishScreen;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record OpenWishScreenPayload() implements CustomPacketPayload {

    public static final Type<OpenWishScreenPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("db_renewed", "open_wish_screen"));

    // Codec vacío porque no mandamos datos, solo trigger
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenWishScreenPayload> STREAM_CODEC =
            StreamCodec.of((buf, payload) -> {}, buf -> new OpenWishScreenPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}