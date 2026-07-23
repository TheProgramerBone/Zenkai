package com.hmc.zenkai.core.network.feature.forms;

import com.hmc.zenkai.Zenkai;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * S2C: snapshot completo de las transformaciones (login y /reload). El cliente reemplaza su
 * registro entero, así la rueda de formas y la pantalla de stats ven siempre lo del servidor.
 */
public record FormSyncPacket(List<FormDef> forms) implements CustomPacketPayload {

    public static final Type<FormSyncPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "form_sync"));

    public static final StreamCodec<FriendlyByteBuf, FormSyncPacket> STREAM_CODEC =
            StreamCodec.composite(
                    FormDef.STREAM_CODEC.apply(ByteBufCodecs.list()), FormSyncPacket::forms,
                    FormSyncPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(FormSyncPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Map<ResourceLocation, FormDef> map = new LinkedHashMap<>();
            for (FormDef d : pkt.forms()) map.put(d.id(), d);
            FormDef.replaceAll(map);
            FormRegistry.rebuild(); // los índices derivados también en cliente
        });
    }
}