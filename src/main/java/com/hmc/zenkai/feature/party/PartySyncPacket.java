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
 * tamaño máximo de {@link PartyManager#HARD_CAP} entradas no compensa la complejidad de
 * diferenciar; se manda entero tras cada mutación (ver PartyService) y al iniciar sesión
 * si ya estaba en una.
 * inParty=false es "ya no tienes party" (kick, leave, disband): el cliente lo usa para
 * vaciar PartyScreen, así que leaderId/members van vacíos en ese caso — no hace falta
 * mandarlos y así el paquete es más corto en el caso común de "no tengo party".
 * maxSize (tope DE ESTA party) y maxSizeCeiling (tope admin, ServerConfig) viajan los dos:
 * PartyScreen necesita maxSize para el encabezado ("Party — 2/6") y maxSizeCeiling para
 * acotar el picker de PartyConfig sin round-trip aparte — ver DrawMaxSizePopup.
 */
public record PartySyncPacket(boolean inParty, @Nullable UUID leaderId, boolean friendlyFire,
                              int maxSize, int maxSizeCeiling, List<Member> members)
        implements CustomPacketPayload {

    public record Member(UUID id, String name) {}

    /** Tope técnico absoluto (protocolo) de miembros por party — ver PartyManager.HARD_CAP,
     *  su única fuente real; duplicado aquí solo como referencia de javadoc. */
    public static final int MAX_MEMBERS = PartyManager.HARD_CAP;

    /** CRASH REAL VISTO EN PRUEBAS: PartyService.resolveName() cae a id.toString() (36
     *  caracteres, formato UUID con guiones) para un miembro sin nombre de jugador real —
     *  offline sin GameProfileCache, o un miembro de prueba de PartyDebug. Con el tope
     *  anterior (32) ese fallback reventaba writeUtf en el encoder y desconectaba al jugador
     *  en CADA login mientras esa party siguiera en el SavedData. 40 deja margen sobre los
     *  36 exactos de un UUID sin que un nombre de jugador normal (máx. 16 de Mojang) se
     *  acerque siquiera al límite.
     */
    private static final int MAX_NAME_LEN = 40;

    public static final Type<PartySyncPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "party_sync"));

    public static final StreamCodec<FriendlyByteBuf, PartySyncPacket> STREAM_CODEC =
            StreamCodec.of(PartySyncPacket::encode, PartySyncPacket::decode);

    private static void encode(FriendlyByteBuf buf, PartySyncPacket pkt) {
        buf.writeBoolean(pkt.inParty());
        if (!pkt.inParty()) return;
        buf.writeUUID(pkt.leaderId());
        buf.writeBoolean(pkt.friendlyFire());
        buf.writeVarInt(pkt.maxSize());
        buf.writeVarInt(pkt.maxSizeCeiling());
        buf.writeVarInt(pkt.members().size());
        for (Member m : pkt.members()) {
            buf.writeUUID(m.id());
            buf.writeUtf(m.name(), MAX_NAME_LEN);
        }
    }

    private static PartySyncPacket decode(FriendlyByteBuf buf) {
        boolean inParty = buf.readBoolean();
        if (!inParty) return new PartySyncPacket(false, null, false, 0, 0, List.of());
        UUID leader = buf.readUUID();
        boolean friendlyFire = buf.readBoolean();
        int maxSize = buf.readVarInt();
        int maxSizeCeiling = buf.readVarInt();
        int n = buf.readVarInt();
        List<Member> members = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            members.add(new Member(buf.readUUID(), buf.readUtf(MAX_NAME_LEN)));
        }
        return new PartySyncPacket(true, leader, friendlyFire, maxSize, maxSizeCeiling, members);
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(PartySyncPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ClientPartyState.onSync(pkt));
    }
}
