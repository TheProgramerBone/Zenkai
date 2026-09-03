package com.hmc.zenkai.feature.teleport;

import com.hmc.zenkai.Zenkai;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * S2C, sin datos: le dice al cliente que abra el menú de planetas (InstantTransmissionMenuScreen).
 * Lo manda InstantTransmissionSystem cuando el jugador suelta TAB tras haberlo armado
 * (mantenerlo quieto 3s, nivel 3+) — mismo patrón que OpenWishScreenPayload/OpenMasterPayload
 * para no meter un `new XScreen()` dentro de ModNetworking (ver el comentario de ese archivo
 * sobre RuntimeDistCleaner en servidor dedicado).
 */
public record OpenInstantTransmissionMenuPayload() implements CustomPacketPayload {
    public static final Type<OpenInstantTransmissionMenuPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "open_instant_transmission_menu"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenInstantTransmissionMenuPayload> STREAM_CODEC =
            StreamCodec.unit(new OpenInstantTransmissionMenuPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
