package com.hmc.zenkai.network;

import com.hmc.zenkai.Zenkai;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Servidor → cliente: abre la elección de raza (tras renunciar y empezar un personaje nuevo). Sin datos. */
public record OpenRaceSelectionPayload() implements CustomPacketPayload {
    public static final Type<OpenRaceSelectionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "open_race_selection"));

    public static final StreamCodec<FriendlyByteBuf, OpenRaceSelectionPayload> STREAM_CODEC =
            StreamCodec.unit(new OpenRaceSelectionPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
