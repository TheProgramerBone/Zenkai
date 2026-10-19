package com.hmc.zenkai.feature.combat;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.combat.GuardClientState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * S2C: aguante de guardia del PROPIO jugador (actual y máximo), para pintar su barra. Solo
 * viaja a él: los demás no necesitan verlo. GuardStamina lo envía cuando cambia de forma
 * visible, no cada tick. brokenTicks > 0 = la guardia acaba de romperse y no puede levantarla
 * durante ese tiempo (el cliente deja de bloquear); -1 = no cambia lo que ya sabía.
 */
public record GuardSyncPacket(float current, float max, int brokenTicks) implements CustomPacketPayload {

    public static final Type<GuardSyncPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "guard_sync"));

    public static final StreamCodec<FriendlyByteBuf, GuardSyncPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, pkt) -> {
                        buf.writeFloat(pkt.current());
                        buf.writeFloat(pkt.max());
                        buf.writeVarInt(pkt.brokenTicks() + 1);
                    },
                    buf -> new GuardSyncPacket(buf.readFloat(), buf.readFloat(), buf.readVarInt() - 1));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(GuardSyncPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> GuardClientState.accept(pkt.current(), pkt.max(), pkt.brokenTicks()));
    }
}
