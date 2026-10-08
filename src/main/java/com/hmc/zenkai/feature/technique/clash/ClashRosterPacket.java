package com.hmc.zenkai.feature.technique.clash;

import com.hmc.zenkai.Zenkai;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * S2C: quién está en el choque, ya del lado de quien lo recibe (`mine` = SU equipo, líder
 * primero). Se reenvía a cada jugador participante cuando cambia algo: al empezar, cuando alguien
 * se une o sale, cuando alguien pulsa y al resolver. Sustituye al antiguo ClashPressedPacket.
 *
 * REGLA DEL CHOQUE: antes del resultado nadie ve el valor de nadie — ni del rival ni de sus
 * compañeros —, solo QUE pulsó: {@code value} viaja a -1 hasta la resolución, y entonces lleva la
 * aportación de cada uno (su barra × el multiplicador de su equipo).
 * {@code multMine}/{@code multRival}: multiplicador del equipo pequeño (ClashRules), para la
 * insignia «×1,6» del HUD.
 */
public record ClashRosterPacket(int clashId, List<Member> mine, List<Member> rival,
                                float multMine, float multRival) implements CustomPacketPayload {

    /** Nombre recortado a esto ANTES de escribirlo (ver la nota de writeUtf de la skill de
     *  paquetes): un nombre personalizado de mob puede ser largo. */
    public static final int NAME_MAX = 64;

    public record Member(int entityId, String name, boolean isPlayer, UUID uuid, boolean pressed,
                         int value) {}

    public static final Type<ClashRosterPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "clash_roster"));

    public static final StreamCodec<FriendlyByteBuf, ClashRosterPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeVarInt(p.clashId());
                        writeList(buf, p.mine());
                        writeList(buf, p.rival());
                        buf.writeFloat(p.multMine());
                        buf.writeFloat(p.multRival());
                    },
                    buf -> new ClashRosterPacket(buf.readVarInt(), readList(buf), readList(buf),
                            buf.readFloat(), buf.readFloat()));

    private static void writeList(FriendlyByteBuf buf, List<Member> list) {
        buf.writeVarInt(list.size());
        for (Member m : list) {
            buf.writeVarInt(m.entityId());
            String n = m.name() == null ? "" : m.name();
            buf.writeUtf(n.length() > NAME_MAX ? n.substring(0, NAME_MAX) : n, NAME_MAX);
            buf.writeBoolean(m.isPlayer());
            buf.writeUUID(m.uuid());
            buf.writeBoolean(m.pressed());
            buf.writeVarInt(m.value() + 1); // -1 (oculto) viaja como 0
        }
    }

    private static List<Member> readList(FriendlyByteBuf buf) {
        int n = Math.min(buf.readVarInt(), 8);
        List<Member> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            list.add(new Member(buf.readVarInt(), buf.readUtf(NAME_MAX), buf.readBoolean(),
                    buf.readUUID(), buf.readBoolean(), buf.readVarInt() - 1));
        }
        return list;
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(ClashRosterPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> com.hmc.zenkai.client.clash.ClashClientState.roster(pkt));
    }
}
