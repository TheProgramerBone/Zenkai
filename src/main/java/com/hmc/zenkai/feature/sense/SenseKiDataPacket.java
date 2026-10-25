package com.hmc.zenkai.feature.sense;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.overlay.SenseKiClientState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * S2C: percepción del Sentir Ki («Resonancia»). Cabecera (nivel con el que se filtró) + una lista
 * de ecos, uno por entidad percibida, con SOLO los campos que autoriza ese nivel:
 *  - nivel 1: id, posición, altura, jugador/criatura y fuerza RELATIVA categorizada;
 *  - nivel 2: + vida en cuatro estados y alineamiento aparente (matiz secundario);
 *  - nivel 3: + vida continua, stamina y ki de quien los tenga;
 *  - nivel 4: + bits de estado (cargando, transformado, suprimiendo, en combate, jefe, te fija);
 *  - nivel 5: + {@link TargetReading} del objetivo fijado (vida exacta, ki/stamina por cuartos).
 *
 * El servidor es la autoridad: lo que un nivel no puede saber NO viaja (valor neutro -1 o bit a
 * cero), en vez de mandarlo entero y esconderlo en el cliente. Por eso el PL ya no viaja: ni el
 * real ni el aparente, solo la categoría relativa a quien siente, calculada con el PL APARENTE
 * (que respeta Ki Control). La cifra exacta es del scouter.
 *
 * La posición viaja porque la entidad puede estar fuera del rango de seguimiento del cliente
 * (el rango del nivel 5 llega a 140 bloques); si el cliente la tiene cargada, usa la suya.
 */
public record SenseKiDataPacket(int level, List<Entry> entries, @Nullable TargetReading reading)
        implements CustomPacketPayload {

    public static final int FLAG_CHARGING = 1;
    public static final int FLAG_TRANSFORMED = 2;
    public static final int FLAG_SUPPRESSING = 4;
    public static final int FLAG_IN_COMBAT = 8;
    public static final int FLAG_BOSS = 16;
    /** Esta entidad te tiene fijado (lock-on de un jugador o objetivo de un mob). */
    public static final int FLAG_TARGETING_YOU = 32;
    /** En mitad de la transformación (la animación); FLAG_TRANSFORMED es la forma ya activa. */
    public static final int FLAG_TRANSFORMING = 64;
    /** Firma DIVINA que quien siente no puede leer (no tiene God Ki): llega tenue y sin datos. */
    public static final int FLAG_DIVINE_UNKNOWN = 128;

    /** Fuerza relativa a quien siente: muy por debajo, inferior, pareja, superior, abrumadora. */
    public static final int STRENGTH_FAINT = 0;
    public static final int STRENGTH_INFERIOR = 1;
    public static final int STRENGTH_COMPARABLE = 2;
    public static final int STRENGTH_SUPERIOR = 3;
    public static final int STRENGTH_OVERWHELMING = 4;

    /** Alineamiento no autorizado por el nivel (por debajo del 2). */
    public static final int NO_ALIGNMENT = Byte.MIN_VALUE;

    /**
     * Un eco.
     * @param health   vida en milésimas (0..1000), -1 si el nivel no la deja ver (nivel 1). Al nivel 2
     *                 llega ya cuantizada a cuartos.
     * @param stamina  milésimas, o -1 si no tiene o el nivel no la deja ver (nivel 3+)
     * @param ki       milésimas, o -1 igual que stamina
     * @param alignment -100..+100, o {@link #NO_ALIGNMENT}
     * @param strength  STRENGTH_*
     */
    public record Entry(int entityId, float x, float y, float z, float height, boolean isPlayer,
                        int strength, int alignment, int health, int stamina, int ki, int flags) {
        public float healthFraction() { return health / 1000f; }
        public boolean hasHealth() { return health >= 0; }
        public boolean hasAlignment() { return alignment != NO_ALIGNMENT; }
        public boolean has(int flag) { return (flags & flag) != 0; }
    }

    /**
     * Lectura del OBJETIVO FIJADO (solo nivel 5): vida exacta y ki/stamina por cuartos. Es la única
     * parte del Ki Sense que enseña cifras, y solo de un objetivo y solo al nivel máximo.
     * @param kiTier  0..4 cuartos de ki que le quedan, o -1 si la entidad no tiene reserva de ki
     * @param staminaTier 0..4 cuartos de stamina, o -1 si no tiene
     */
    public record TargetReading(int entityId, int body, int bodyMax, int kiTier, int staminaTier) {}

    public static final Type<SenseKiDataPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "sense_ki_data"));

    public static final StreamCodec<FriendlyByteBuf, SenseKiDataPacket> STREAM_CODEC =
            StreamCodec.of(SenseKiDataPacket::encode, SenseKiDataPacket::decode);

    private static void encode(FriendlyByteBuf buf, SenseKiDataPacket pkt) {
        buf.writeByte(pkt.level());
        buf.writeVarInt(pkt.entries().size());
        for (Entry e : pkt.entries()) {
            buf.writeVarInt(e.entityId());
            buf.writeFloat(e.x());
            buf.writeFloat(e.y());
            buf.writeFloat(e.z());
            buf.writeFloat(e.height());
            buf.writeBoolean(e.isPlayer());
            buf.writeByte(e.strength());
            buf.writeByte(e.alignment());
            buf.writeShort(e.health());
            buf.writeShort(e.stamina());
            buf.writeShort(e.ki());
            buf.writeByte(e.flags());
        }
        TargetReading r = pkt.reading();
        buf.writeBoolean(r != null);
        if (r != null) {
            buf.writeVarInt(r.entityId());
            buf.writeVarInt(r.body());
            buf.writeVarInt(r.bodyMax());
            buf.writeByte(r.kiTier());
            buf.writeByte(r.staminaTier());
        }
    }

    private static SenseKiDataPacket decode(FriendlyByteBuf buf) {
        int level = buf.readByte();
        int n = buf.readVarInt();
        List<Entry> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            list.add(new Entry(buf.readVarInt(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
                    buf.readFloat(), buf.readBoolean(), buf.readByte(), buf.readByte(),
                    buf.readShort(), buf.readShort(), buf.readShort(), buf.readByte()));
        }
        TargetReading reading = null;
        if (buf.readBoolean()) {
            reading = new TargetReading(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    buf.readByte(), buf.readByte());
        }
        return new SenseKiDataPacket(level, list, reading);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SenseKiDataPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> SenseKiClientState.onData(pkt.level(), pkt.entries(), pkt.reading()));
    }
}
