package com.hmc.zenkai.feature.combat;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.combat.GrabClientState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * S2C, SOLO al jugador agarrado: estado de su agarre y del skill check para zafarse (GrabSkillCheck). Lo manda
 * PhysicalCombatServer al agarrar, al empezar cada check, al resolverlo y al soltar.
 *
 * @param held        false = ya no está agarrado (el cliente apaga la barra)
 * @param checkIndex  número del check en curso, o -1 si ahora toca la pausa entre checks
 * @param seed        semilla de la zona (junto a checkIndex da la misma zona en ambos lados)
 * @param zoneSize    tamaño de la zona, fracción de la barra
 * @param needleTicks ticks que tarda la aguja en cruzar la barra una vez
 * @param checksDone  aciertos hasta ahora
 * @param checksNeeded aciertos que hacen falta para zafarse
 * @param holdTicks   ticks que le quedan al agarre
 * @param lastResult  0 = nada nuevo, 1 = acertó, 2 = falló (para el destello de la barra)
 */
public record GrabStatePacket(boolean held, int checkIndex, long seed, float zoneSize, int needleTicks,
                              int checksDone, int checksNeeded, int holdTicks, int lastResult)
        implements CustomPacketPayload {

    public static final Type<GrabStatePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "grab_state"));

    public static final StreamCodec<FriendlyByteBuf, GrabStatePacket> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeBoolean(p.held());
                buf.writeVarInt(p.checkIndex() + 1);   // -1 no cabe en un varint sin signo
                buf.writeLong(p.seed());
                buf.writeFloat(p.zoneSize());
                buf.writeVarInt(p.needleTicks());
                buf.writeVarInt(p.checksDone());
                buf.writeVarInt(p.checksNeeded());
                buf.writeVarInt(p.holdTicks());
                buf.writeVarInt(p.lastResult());
            },
            buf -> new GrabStatePacket(buf.readBoolean(), buf.readVarInt() - 1, buf.readLong(), buf.readFloat(),
                    buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));

    /** Paquete de «ya no estás agarrado». */
    public static GrabStatePacket released() {
        return new GrabStatePacket(false, -1, 0L, 0f, 1, 0, 0, 0, 0);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(GrabStatePacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> GrabClientState.accept(pkt));
    }
}
