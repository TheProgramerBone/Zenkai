package com.hmc.zenkai.feature.rage;

import com.hmc.zenkai.Zenkai;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * S2C: la crónica del jugador, en orden cronológico (la más antigua primero), como respuesta a
 * {@link RageChronicleRequestPacket}. Solo lectura: la pantalla no puede modificar nada.
 *
 * Los textos se TRUNCAN al escribir al máximo de cada campo: superar el máximo de {@code writeUtf} no rechaza el paquete,
 * tira la conexión entera. {@code related} es el único que puede venir de fuera (el nombre de una mascota puede ser largo y
 * libre), por eso su tope es el más estricto; el resto son ids y nombres de enumerados que el propio mod genera.
 */
public record RageChronicleSyncPacket(List<RageChronicle.Entry> entries) implements CustomPacketPayload {

    public static final Type<RageChronicleSyncPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "rage_chronicle_sync"));

    public static final StreamCodec<FriendlyByteBuf, RageChronicleSyncPacket> STREAM_CODEC =
            StreamCodec.of(RageChronicleSyncPacket::encode, RageChronicleSyncPacket::decode);

    static final int MAX_ID = 96, MAX_TRIGGER = 32, MAX_FORM = 128, MAX_ENUM = 16, MAX_RELATED = 64;

    private static String cut(String s, int max) { return s.length() <= max ? s : s.substring(0, max); }

    private static void encode(FriendlyByteBuf buf, RageChronicleSyncPacket pkt) {
        int n = Math.min(pkt.entries().size(), RageChronicle.CAP);
        buf.writeVarInt(n);
        // Las n más recientes si por algún motivo llegaran más (el anillo no debería permitirlo).
        List<RageChronicle.Entry> list = pkt.entries().subList(pkt.entries().size() - n, pkt.entries().size());
        for (RageChronicle.Entry e : list) {
            buf.writeUtf(cut(e.eventId(), MAX_ID), MAX_ID);
            buf.writeUtf(cut(e.trigger(), MAX_TRIGGER), MAX_TRIGGER);
            buf.writeUtf(cut(e.targetForm(), MAX_FORM), MAX_FORM);
            buf.writeUtf(cut(e.result(), MAX_ENUM), MAX_ENUM);
            buf.writeUtf(cut(e.acquisition(), MAX_ENUM), MAX_ENUM);
            buf.writeUtf(cut(e.related(), MAX_RELATED), MAX_RELATED);
            buf.writeLong(e.worldTime());
            buf.writeFloat(e.masteryBefore());
            buf.writeFloat(e.masteryAfter());
            buf.writeBoolean(e.cinematicPlayed());
            buf.writeBoolean(e.interrupted());
            buf.writeBoolean(e.ignoredByCooldown());
        }
    }

    private static RageChronicleSyncPacket decode(FriendlyByteBuf buf) {
        int n = Math.min(buf.readVarInt(), RageChronicle.CAP);   // un servidor no puede forzar una lista enorme
        List<RageChronicle.Entry> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            out.add(new RageChronicle.Entry(buf.readUtf(MAX_ID), buf.readUtf(MAX_TRIGGER), buf.readUtf(MAX_FORM),
                    buf.readUtf(MAX_ENUM), buf.readUtf(MAX_ENUM), buf.readUtf(MAX_RELATED), buf.readLong(),
                    buf.readFloat(), buf.readFloat(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean()));
        }
        return new RageChronicleSyncPacket(out);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(RageChronicleSyncPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> RageChronicleClientState.onSync(pkt.entries()));
    }
}
