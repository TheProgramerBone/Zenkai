package com.hmc.zenkai.feature.dialogue;

import com.hmc.zenkai.Zenkai;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Servidor → cliente: abre la pantalla de diálogo de un NPC. Los textos viajan como {@link Component} (claves de lang con
 * sus argumentos ya puestos), porque el servidor no sabe qué idioma tiene el cliente. Las opciones solo se enseñan tras la
 * última página.
 */
public record OpenDialoguePayload(int entityId, Component speaker, List<Component> pages, List<Choice> choices)
        implements CustomPacketPayload {

    public record Choice(String id, Component label) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Choice> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(32), Choice::id,
                ComponentSerialization.STREAM_CODEC, Choice::label,
                Choice::new);
    }

    public static final Type<OpenDialoguePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "dialogue_open"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenDialoguePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, OpenDialoguePayload::entityId,
            ComponentSerialization.STREAM_CODEC, OpenDialoguePayload::speaker,
            ByteBufCodecs.collection(ArrayList::new, ComponentSerialization.STREAM_CODEC), OpenDialoguePayload::pages,
            ByteBufCodecs.collection(ArrayList::new, Choice.STREAM_CODEC), OpenDialoguePayload::choices,
            OpenDialoguePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
