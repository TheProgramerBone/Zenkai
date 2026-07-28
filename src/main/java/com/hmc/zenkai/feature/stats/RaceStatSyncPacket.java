package com.hmc.zenkai.feature.stats;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.feature.Race;
import com.hmc.zenkai.feature.RaceStatTable;
import com.hmc.zenkai.feature.Style;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.EnumMap;
import java.util.Map;

/**
 * S2C: snapshot completo de los coeficientes de stat por raza (login y /reload).
 * El cliente reemplaza su tabla entera, igual que SkillSyncPacket: la pantalla de stats y
 * los previews del editor siempre ven los números del servidor, no los compilados.
 *
 * Formato plano (raza, estilo, RaceStatTable.COLS doubles) en vez de un mapa anidado: son
 * 15 filas fijas, así que un codec compuesto no aporta nada y esto se lee de un vistazo.
 */
public record RaceStatSyncPacket(Map<Race, Map<Style, double[]>> table,
                                 Map<Race, int[]> bases) implements CustomPacketPayload {

    public static final Type<RaceStatSyncPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "race_stat_sync"));

    public static final StreamCodec<FriendlyByteBuf, RaceStatSyncPacket> STREAM_CODEC =
            StreamCodec.of(RaceStatSyncPacket::write, RaceStatSyncPacket::read);

    /** Snapshot de lo que hay cargado ahora mismo en el servidor. */
    public static RaceStatSyncPacket snapshot() {
        Map<Race, Map<Style, double[]>> out = new EnumMap<>(Race.class);
        for (Race race : Race.values()) {
            Map<Style, double[]> byStyle = new EnumMap<>(Style.class);
            for (Style style : Style.values()) {
                double[] row = RaceStatTable.row(race, style);
                if (row != null) byStyle.put(style, row);
            }
            if (!byStyle.isEmpty()) out.put(race, byStyle);
        }
        Map<Race, int[]> bases = new EnumMap<>(Race.class);
        for (Race race : Race.values()) bases.put(race, RaceStatTable.baseAttributes(race));
        return new RaceStatSyncPacket(out, bases);
    }

    private static void write(FriendlyByteBuf buf, RaceStatSyncPacket pkt) {
        int rows = 0;
        for (var e : pkt.table().values()) rows += e.size();
        buf.writeVarInt(rows);
        for (var raceEntry : pkt.table().entrySet()) {
            for (var styleEntry : raceEntry.getValue().entrySet()) {
                buf.writeEnum(raceEntry.getKey());
                buf.writeEnum(styleEntry.getKey());
                // Longitud FIJA a COLS, no el largo del array: una fila corta (datapack de
                // otra versión) desincronizaría el buffer y el resto del paquete saldría basura.
                double[] row = styleEntry.getValue();
                for (int j = 0; j < RaceStatTable.COLS; j++) {
                    buf.writeDouble(j < row.length ? row[j] : 1.0);
                }
            }
        }
        buf.writeVarInt(pkt.bases().size());
        for (var e : pkt.bases().entrySet()) {
            buf.writeEnum(e.getKey());
            for (int v : e.getValue()) buf.writeVarInt(v);
        }
    }

    private static RaceStatSyncPacket read(FriendlyByteBuf buf) {
        int rows = buf.readVarInt();
        Map<Race, Map<Style, double[]>> out = new EnumMap<>(Race.class);
        for (int i = 0; i < rows; i++) {
            Race race = buf.readEnum(Race.class);
            Style style = buf.readEnum(Style.class);
            double[] vals = new double[RaceStatTable.COLS];
            for (int j = 0; j < RaceStatTable.COLS; j++) vals[j] = buf.readDouble();
            out.computeIfAbsent(race, k -> new EnumMap<>(Style.class)).put(style, vals);
        }
        int nBases = buf.readVarInt();
        Map<Race, int[]> bases = new EnumMap<>(Race.class);
        for (int i = 0; i < nBases; i++) {
            Race race = buf.readEnum(Race.class);
            int[] vals = new int[6];   // atributos: STR, CON, DEX, WIL, SPI, MND — fijos
            for (int j = 0; j < 6; j++) vals[j] = buf.readVarInt();
            bases.put(race, vals);
        }
        return new RaceStatSyncPacket(out, bases);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(RaceStatSyncPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            RaceStatTable.replaceAll(pkt.table());
            RaceStatTable.replaceBases(pkt.bases());
        });
    }
}