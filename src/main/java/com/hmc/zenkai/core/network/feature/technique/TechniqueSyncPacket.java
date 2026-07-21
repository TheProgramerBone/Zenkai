package com.hmc.zenkai.core.network.feature.technique;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.core.technique.TechniqueDef;
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
 * S2C: snapshot completo de los números de técnica (login y /reload). El cliente
 * reemplaza su registro entero: el editor y el overlay siempre ven lo del servidor.
 */
public record TechniqueSyncPacket(List<TechniqueDef> techniques) implements CustomPacketPayload {

    public static final Type<TechniqueSyncPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "technique_sync"));

    public static final StreamCodec<FriendlyByteBuf, TechniqueSyncPacket> STREAM_CODEC =
            StreamCodec.composite(
                    TechniqueDef.STREAM_CODEC.apply(ByteBufCodecs.list()), TechniqueSyncPacket::techniques,
                    TechniqueSyncPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(TechniqueSyncPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Map<String, TechniqueDef> map = new LinkedHashMap<>();
            for (TechniqueDef d : pkt.techniques()) map.put(d.kind().name() + "/" + d.id(), d);
            TechniqueDef.replaceAll(map);
        });
    }
}