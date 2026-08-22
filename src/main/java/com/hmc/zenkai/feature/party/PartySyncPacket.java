package com.hmc.zenkai.feature.party;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.party.ClientPartyState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * S2C: estado COMPLETO de la party del jugador que lo recibe, nunca un delta. Con un
 * tamaño máximo de {@link PartyManager#MAX_SIZE} entradas no compensa la complejidad de
 * diferenciar; se manda entero tras cada mutación (ver PartyService) y al iniciar sesión
 * si ya estaba en una.
 * inParty=false es "ya no tienes party" (kick, leave, disband): el cliente lo usa para
 * vaciar PartyScreen, así que leaderId/members van vacíos en ese caso — no hace falta
 * mandarlos y así el paquete es más corto en el caso común de "no tengo party".
 */
public record PartySyncPacket(boolean inParty, @Nullable UUID leaderId, boolean friendlyFire,
                              List<Member> members) implements CustomPacketPayload {

    public record Member(UUID id, String name) {}

    public static final Type<PartySyncPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "party_sync"));

    public static final StreamCodec<FriendlyByteBuf, PartySyncPacket> STREAM_CODEC =
            StreamCodec.of(PartySyncPacket::encode, PartySyncPacket::decode);

    private static void encode(FriendlyByteBuf buf, PartySyncPacket pkt) {
        buf.writeBoolean(pkt.inParty());
        if (!pkt.inParty()) return;
        buf.writeUUID(pkt.leaderId());
        buf.writeBoolean(pkt.friendlyFire());
        buf.writeVarInt(pkt.members().size());
        for (Member m : pkt.members()) {
            buf.writeUUID(m.id());
            buf.writeUtf(m.name(), 32);
        }
    }

    private static PartySyncPacket decode(FriendlyByteBuf buf) {
        boolean inParty = buf.readBoolean();
        if (!inParty) return new PartySyncPacket(false, null, false, List.of());
        UUID leader = buf.readUUID();
        boolean friendlyFire = buf.readBoolean();
        int n = buf.readVarInt();
        List<Member> members = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            members.add(new Member(buf.readUUID(), buf.readUtf(32)));
        }
        return new PartySyncPacket(true, leader, friendlyFire, members);
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(PartySyncPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ClientPartyState.onSync(pkt));
    }
}
