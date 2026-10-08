package com.hmc.zenkai.feature.technique.clash;

import com.hmc.zenkai.Zenkai;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * S2C: cierre del minijuego para un participante. `result`: {@link #WIN}, {@link #LOSE},
 * {@link #TIE} o {@link #ABORT} (el choque se canceló sin ganador: alguien murió, se desconectó o
 * un proyectil desapareció). Los valores van de 0 a unos 1100 (cursor + bonus), ya del lado de
 * quien recibe el packet: `mine` es SIEMPRE el suyo.
 * Con ABORT los valores no significan nada y el cliente solo limpia su estado.
 */
public record ClashResultPacket(int clashId, int result, int mine, int rival, int mineStage,
                                int rivalStage)
        implements CustomPacketPayload {

    public static final int WIN = 0, LOSE = 1, TIE = 2, ABORT = 3;

    public static final Type<ClashResultPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "clash_result"));

    public static final StreamCodec<FriendlyByteBuf, ClashResultPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeVarInt(p.clashId());
                        buf.writeByte(p.result());
                        buf.writeVarInt(p.mine());
                        buf.writeVarInt(p.rival());
                        buf.writeByte(p.mineStage());
                        buf.writeByte(p.rivalStage());
                    },
                    buf -> new ClashResultPacket(buf.readVarInt(), buf.readByte(),
                            buf.readVarInt(), buf.readVarInt(), buf.readByte(), buf.readByte()));

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(ClashResultPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> com.hmc.zenkai.client.clash.ClashClientState.result(pkt));
    }
}
