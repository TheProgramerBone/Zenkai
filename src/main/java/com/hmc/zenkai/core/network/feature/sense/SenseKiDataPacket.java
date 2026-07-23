package com.hmc.zenkai.core.network.feature.sense;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.SenseKiClientState;
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

    /** id + vida + estamina + ki + PL + si es jugador (para los filtros de modo).
     *  Estamina y ki van a 0 en lo que no tenga pools Zenkai; el cliente decide si los
     *  muestra según el nivel de Ki Sense. */
    /** id + vida + estamina + ki + alineamiento + PL + si es jugador.
     *  Los pools van a 0 en lo que no tenga stats Zenkai; el cliente decide qué muestra
     *  según el nivel de Ki Sense. */
    public record Entry(int entityId, int body, int bodyMax,
                        int stamina, int staminaMax, int energy, int energyMax,
                        int alignment, long powerLevel, boolean isPlayer) {}

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
        }
    }

    private static SenseKiDataPacket decode(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        List<Entry> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            list.add(new Entry(buf.readVarInt(),
                    buf.readInt(), buf.readInt(),
                    buf.readInt(), buf.readInt(),
                    buf.readInt(), buf.readInt(),
                    buf.readVarInt(),
                    buf.readLong(), buf.readBoolean()));
        }
        return new SenseKiDataPacket(list);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SenseKiDataPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> SenseKiClientState.onData(pkt.entries()));
    }
}