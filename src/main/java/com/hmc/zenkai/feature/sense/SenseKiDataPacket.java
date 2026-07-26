package com.hmc.zenkai.feature.sense;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.overlay.SenseKiClientState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * S2C: respuesta del escaneo de sentir el ki. Lista de entidades en rango con su vida (body real
 * si tienen stats; vanilla si no) y su PL. El cliente la cachea y filtra por modo al renderizar.
 */
public record SenseKiDataPacket(List<Entry> entries) implements CustomPacketPayload {

    /** Id + vida + stamina + ki + alineamiento + PL + si es jugador.
     *  Los pools van a 0 en lo que no tenga stats Zenkai; el cliente decide qué muestra
     *  según el nivel de Ki Sense.
     *
     *  melee/defense/kiPower son el DESGLOSE del PL y llegan a 0 salvo en un caso: la entidad
     *  que tienes FIJADA con el lock-on, y solo si tu Ki Sense está al máximo. El filtro es
     *  del servidor a propósito — mandarlos siempre y ocultarlos en la GUI sería un gate
     *  falso que cualquier cliente modificado se salta. */
    public record Entry(int entityId, int body, int bodyMax,
                        int stamina, int staminaMax, int energy, int energyMax,
                        int alignment, long powerLevel, boolean isPlayer,
                        long melee, long defense, long kiPower) {

        /** Constructor corto para lo que no lleva desglose. */
        public Entry(int entityId, int body, int bodyMax,
                     int stamina, int staminaMax, int energy, int energyMax,
                     int alignment, long powerLevel, boolean isPlayer) {
            this(entityId, body, bodyMax, stamina, staminaMax, energy, energyMax,
                    alignment, powerLevel, isPlayer, 0L, 0L, 0L);
        }

        /** ¿Trae los números exactos? (fijado + Ki Sense al máximo) */
        public boolean hasBreakdown() {
            return melee > 0 || defense > 0 || kiPower > 0;
        }
    }

    public static final Type<SenseKiDataPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "sense_ki_data"));

    public static final StreamCodec<FriendlyByteBuf, SenseKiDataPacket> STREAM_CODEC =
            StreamCodec.of(SenseKiDataPacket::encode, SenseKiDataPacket::decode);

    private static void encode(FriendlyByteBuf buf, SenseKiDataPacket pkt) {
        buf.writeVarInt(pkt.entries().size());
        for (Entry e : pkt.entries()) {
            buf.writeVarInt(e.entityId());
            buf.writeInt(e.body());
            buf.writeInt(e.bodyMax());
            buf.writeInt(e.stamina());
            buf.writeInt(e.staminaMax());
            buf.writeInt(e.energy());
            buf.writeInt(e.energyMax());
            buf.writeVarInt(e.alignment());
            buf.writeLong(e.powerLevel());
            buf.writeBoolean(e.isPlayer());
            // Desglose: un solo boolean ahorra 24 bytes por entidad y el scan manda hasta 128.
            boolean bd = e.hasBreakdown();
            buf.writeBoolean(bd);
            if (bd) {
                buf.writeLong(e.melee());
                buf.writeLong(e.defense());
                buf.writeLong(e.kiPower());
            }
        }
    }

    private static SenseKiDataPacket decode(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        List<Entry> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            int id = buf.readVarInt();
            int body = buf.readInt(), bodyMax = buf.readInt();
            int stam = buf.readInt(), stamMax = buf.readInt();
            int energy = buf.readInt(), energyMax = buf.readInt();
            int align = buf.readVarInt();
            long pl = buf.readLong();
            boolean isPlayer = buf.readBoolean();
            long melee = 0L, defense = 0L, kiPower = 0L;
            if (buf.readBoolean()) {
                melee = buf.readLong();
                defense = buf.readLong();
                kiPower = buf.readLong();
            }
            list.add(new Entry(id, body, bodyMax, stam, stamMax, energy, energyMax,
                    align, pl, isPlayer, melee, defense, kiPower));
        }
        return new SenseKiDataPacket(list);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SenseKiDataPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> SenseKiClientState.onData(pkt.entries()));
    }
}