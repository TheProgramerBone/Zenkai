package com.hmc.zenkai.feature.race.hair;

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

/** S2C: snapshot completo del catálogo de peinados (login y /reload). Espeja FaceCatalogSyncPacket. */
public record HairCatalogSyncPacket(List<HairStyleDef> styles) implements CustomPacketPayload {

    public static final Type<HairCatalogSyncPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "hair_catalog_sync"));

    public static final StreamCodec<FriendlyByteBuf, HairCatalogSyncPacket> STREAM_CODEC =
            StreamCodec.composite(
                    HairStyleDef.STREAM_CODEC.apply(ByteBufCodecs.list()), HairCatalogSyncPacket::styles,
                    HairCatalogSyncPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(HairCatalogSyncPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Map<ResourceLocation, HairStyleDef> map = new LinkedHashMap<>();
            for (HairStyleDef d : pkt.styles()) map.put(d.id(), d);
            HairCatalog.replaceAll(map);
        });
    }
}
