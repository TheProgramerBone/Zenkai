package com.hmc.zenkai.feature.aura;

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
 * S2C: snapshot completo de las firmas de aura por aura_type (login y /reload). Mismo patrón
 * que FormSyncPacket: una entrada por tipo, el cliente reemplaza su registro entero.
 */
public record AuraSignatureSyncPacket(List<Entry> entries) implements CustomPacketPayload {

    /** Cap del key ("aura_type", nombre de archivo de datapack: arcosian, golden...). Los 5
     *  valores en uso hoy miden 4-8 caracteres; 64 da margen amplio para cualquier aura_type
     *  futuro de un addon sin acercarse al límite — dimensionado para el peor caso que este
     *  mod NO controla (lo elige quien escriba el datapack), no para el caso típico de hoy;
     *  ver el incidente de PartySyncPacket documentado en CLAUDE.md. */
    public static final int MAX_TYPE_LEN = 64;

    /**
     * El codec de AuraModifier vive aquí (no como STREAM_CODEC estático en la propia clase)
     * a propósito: AuraModifier no importa nada de Minecraft, lo que permite que AuraSelfTest
     * se ejecute sin arrancar el juego (ver su javadoc) — un campo estático de tipo
     * StreamCodec/FriendlyByteBuf en AuraModifier forzaría la carga de esas clases en su
     * <clinit> incluso para ese uso standalone.
     */
    public record Entry(String auraType, AuraModifier modifier) {
        public static final StreamCodec<FriendlyByteBuf, Entry> STREAM_CODEC = StreamCodec.of(
                (buf, e) -> {
                    buf.writeUtf(e.auraType(), MAX_TYPE_LEN);
                    AuraModifier m = e.modifier();
                    buf.writeFloat(m.dMass());
                    buf.writeFloat(m.dSpike());
                    buf.writeFloat(m.dTurb());
                    buf.writeFloat(m.dSpread());
                    buf.writeFloat(m.dHeight());
                    buf.writeFloat(m.dDensity());
                    buf.writeFloat(m.turbGain());
                    buf.writeFloat(m.spreadGain());
                    buf.writeFloat(m.pulseHzGain());
                    buf.writeFloat(m.pulseAmpGain());
                    buf.writeBoolean(m.additiveGlow());
                    buf.writeBoolean(m.electricSparks());
                    buf.writeBoolean(m.fireEmbers());
                },
                buf -> new Entry(buf.readUtf(MAX_TYPE_LEN), new AuraModifier(
                        buf.readFloat(), buf.readFloat(), buf.readFloat(),
                        buf.readFloat(), buf.readFloat(), buf.readFloat(),
                        buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
                        buf.readBoolean(), buf.readBoolean(), buf.readBoolean())));
    }

    public static final Type<AuraSignatureSyncPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "aura_signature_sync"));

    public static final StreamCodec<FriendlyByteBuf, AuraSignatureSyncPacket> STREAM_CODEC =
            StreamCodec.composite(
                    Entry.STREAM_CODEC.apply(ByteBufCodecs.list()), AuraSignatureSyncPacket::entries,
                    AuraSignatureSyncPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(AuraSignatureSyncPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Map<String, AuraModifier> map = new LinkedHashMap<>();
            for (Entry e : pkt.entries()) map.put(e.auraType(), e.modifier());
            AuraSignatureRegistry.replaceAll(map);
        });
    }
}
