package com.hmc.zenkai.feature.wishes;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.api.wish.CustomWish;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * S2C: los deseos de addons que ESTE jugador puede pedir a ESTE dragón (id, texto e ítem de icono). Se envía justo
 * antes de {@code OpenWishScreenPayload}. El cliente solo lo usa para pintar botones; no decide nada.
 */
public record CustomWishListPayload(List<Entry> wishes) implements CustomPacketPayload {

    public record Entry(ResourceLocation id, Component name, ResourceLocation iconItem) {
        public static Entry of(CustomWish w) { return new Entry(w.id(), w.name(), w.iconItem()); }
    }

    public static final Type<CustomWishListPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "custom_wish_list"));

    private static final StreamCodec<RegistryFriendlyByteBuf, Entry> ENTRY_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, Entry::id,
            ComponentSerialization.STREAM_CODEC, Entry::name,
            ResourceLocation.STREAM_CODEC, Entry::iconItem,
            Entry::new);

    public static final StreamCodec<RegistryFriendlyByteBuf, CustomWishListPayload> STREAM_CODEC = StreamCodec.composite(
            ENTRY_CODEC.apply(ByteBufCodecs.list(64)), CustomWishListPayload::wishes,
            CustomWishListPayload::new);

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }
}
