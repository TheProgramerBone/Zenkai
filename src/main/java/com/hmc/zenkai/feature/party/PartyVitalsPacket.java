package com.hmc.zenkai.feature.party;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.party.ClientPartyState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * S2C: signos vitales de los compañeros que el jugador eligió ver en su panel de party, en
 * milésimas (0..1000) y SIN cifras absolutas: el panel solo dibuja barras. -1 = ese compañero no
 * tiene esa reserva (jugador sin raza, por ejemplo) y la barra no se pinta.
 * Viaja por separado de PartySyncPacket porque cambia cada pocos ticks y PartySyncPacket solo debe
 * mandarse cuando cambia la party. Funciona a cualquier distancia: no depende de que el compañero
 * esté cargado como entidad en el cliente.
 */
public record PartyVitalsPacket(List<Vital> vitals) implements CustomPacketPayload {

    public record Vital(UUID id, int body, int stamina, int ki) {}

    public static final Type<PartyVitalsPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "party_vitals"));

    public static final StreamCodec<FriendlyByteBuf, PartyVitalsPacket> STREAM_CODEC =
            StreamCodec.of(PartyVitalsPacket::encode, PartyVitalsPacket::decode);

    private static void encode(FriendlyByteBuf buf, PartyVitalsPacket pkt) {
        buf.writeVarInt(pkt.vitals().size());
        for (Vital v : pkt.vitals()) {
            buf.writeUUID(v.id());
            buf.writeShort(v.body());
            buf.writeShort(v.stamina());
            buf.writeShort(v.ki());
        }
    }

    private static PartyVitalsPacket decode(FriendlyByteBuf buf) {
        int n = Math.min(buf.readVarInt(), PartyVitalsServer.MAX_SHOWN);
        List<Vital> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            out.add(new Vital(buf.readUUID(), buf.readShort(), buf.readShort(), buf.readShort()));
        }
        return new PartyVitalsPacket(out);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(PartyVitalsPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ClientPartyState.onVitals(pkt));
    }
}
