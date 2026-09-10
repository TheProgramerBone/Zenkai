package com.hmc.zenkai.feature.spacepod;

import com.hmc.zenkai.Zenkai;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * S2C, sin datos: le dice al cliente que abra el menú galáctico (GalacticMenuScreen).
 * Lo manda SpacePodEntity.mobInteract cuando el jugador hace click derecho estando YA montado
 * en la nave — mismo patrón que OpenInstantTransmissionMenuPayload/OpenWishScreenPayload para
 * no meter un `new GalacticMenuScreen()` (clase de cliente) dentro de ModNetworking (ver el
 * comentario de OpenInstantTransmissionMenuPayload sobre RuntimeDistCleaner en servidor
 * dedicado).
 */
public record OpenGalacticMenuPayload() implements CustomPacketPayload {
    public static final Type<OpenGalacticMenuPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "open_galactic_menu"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenGalacticMenuPayload> STREAM_CODEC =
            StreamCodec.unit(new OpenGalacticMenuPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
