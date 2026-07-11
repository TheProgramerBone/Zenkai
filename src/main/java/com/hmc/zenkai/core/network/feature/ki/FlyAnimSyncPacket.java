package com.hmc.zenkai.core.network.feature.ki;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.ClientFlyAnimState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * S2C: estado de animación de vuelo de OTRO jugador (por entityId). El cliente lo guarda en
 * ClientFlyAnimState y ClientZenkaiPalTick alimenta con él el mismo estado-máquina que usa el
 * jugador local (starts -> loops incluidos).
 */
public record FlyAnimSyncPacket(int entityId, boolean flying, byte dir, boolean boosting)
        implements CustomPacketPayload {

    public static final Type<FlyAnimSyncPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "fly_anim_sync"));

    public static final StreamCodec<FriendlyByteBuf, FlyAnimSyncPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, pkt) -> {
                        buf.writeVarInt(pkt.entityId());
                        buf.writeBoolean(pkt.flying());
                        buf.writeByte(pkt.dir());
                        buf.writeBoolean(pkt.boosting());
                    },
                    buf -> new FlyAnimSyncPacket(buf.readVarInt(), buf.readBoolean(),
                            buf.readByte(), buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(FlyAnimSyncPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() ->
                ClientFlyAnimState.onSync(pkt.entityId(), pkt.flying(), pkt.dir(), pkt.boosting()));
    }
}