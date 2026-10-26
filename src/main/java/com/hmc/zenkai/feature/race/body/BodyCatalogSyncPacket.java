package com.hmc.zenkai.feature.race.body;

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

/** S2C: snapshot completo del catálogo de opciones de cuerpo (login y /reload). Espeja FaceCatalogSyncPacket. */
public record BodyCatalogSyncPacket(List<BodyOptionDef> options) implements CustomPacketPayload {

    public static final Type<BodyCatalogSyncPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "body_catalog_sync"));

    public static final StreamCodec<FriendlyByteBuf, BodyCatalogSyncPacket> STREAM_CODEC =
            StreamCodec.composite(
                    BodyOptionDef.STREAM_CODEC.apply(ByteBufCodecs.list()), BodyCatalogSyncPacket::options,
                    BodyCatalogSyncPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(BodyCatalogSyncPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Map<ResourceLocation, BodyOptionDef> map = new LinkedHashMap<>();
            for (BodyOptionDef d : pkt.options()) map.put(d.id(), d);
            BodyCatalog.replaceAll(map);
        });
    }
}
