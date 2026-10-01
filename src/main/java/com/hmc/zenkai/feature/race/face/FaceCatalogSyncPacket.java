package com.hmc.zenkai.feature.race.face;

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
 * S2C: snapshot completo del catálogo de rasgos faciales (login y /reload). El cliente
 * reemplaza el suyo entero: así un mismo id significa el mismo rasgo para cualquier jugador,
 * pase lo que pase con el orden de los archivos o los resource packs de cada uno.
 */
public record FaceCatalogSyncPacket(List<FacePartDef> parts) implements CustomPacketPayload {

    public static final Type<FaceCatalogSyncPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "face_catalog_sync"));

    public static final StreamCodec<FriendlyByteBuf, FaceCatalogSyncPacket> STREAM_CODEC =
            StreamCodec.composite(
                    FacePartDef.STREAM_CODEC.apply(ByteBufCodecs.list()), FaceCatalogSyncPacket::parts,
                    FaceCatalogSyncPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(FaceCatalogSyncPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Map<ResourceLocation, FacePartDef> map = new LinkedHashMap<>();
            for (FacePartDef d : pkt.parts()) map.put(d.id(), d);
            FaceCatalog.replaceAll(map);
        });
    }
}
