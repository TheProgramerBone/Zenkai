package com.hmc.zenkai.feature.combat;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.combat.StunClientState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * S2C: una entidad queda aturdida durante {@code ticks} (0 = deja de estarlo). Lo difunde
 * StunState.apply (immunityTicks = la inmunidad que empieza al acabar, para el icono) a quienes la ven y, si es un jugador, a él mismo (para anular su input).
 * visible = false: el input se anula igual, pero sin estrellas ni icono (la inmovilidad de la escena de transformación, AwakeningProtection).
 */
public record StunSyncPacket(int entityId, int ticks, int immunityTicks, boolean visible) implements CustomPacketPayload {

    public static final Type<StunSyncPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "stun_sync"));

    public static final StreamCodec<FriendlyByteBuf, StunSyncPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, pkt) -> {
                        buf.writeVarInt(pkt.entityId());
                        buf.writeVarInt(pkt.ticks());
                        buf.writeVarInt(pkt.immunityTicks());
                        buf.writeBoolean(pkt.visible());
                    },
                    buf -> new StunSyncPacket(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(StunSyncPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> StunClientState.accept(pkt.entityId(), pkt.ticks(), pkt.immunityTicks(), pkt.visible()));
    }
}
