package com.hmc.zenkai.feature.teleport;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.InstantTransmissionClientState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * S2C, self-only (mandado al propio jugador, nunca a trackers): cooldown restante para el
 * badge del HUD (ClientZenkaiHooks). No lleva más estado porque el HUD de la Fase 1 solo enseña
 * la cuenta atrás del cooldown; el aviso de "menú listo" viaja como action bar normal
 * (Player#displayClientMessage), no por este packet.
 */
public record InstantTransmissionSyncPacket(int cooldownTicks) implements CustomPacketPayload {

    public static final Type<InstantTransmissionSyncPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "instant_transmission_sync"));

    public static final StreamCodec<FriendlyByteBuf, InstantTransmissionSyncPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, InstantTransmissionSyncPacket::cooldownTicks,
                    InstantTransmissionSyncPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(InstantTransmissionSyncPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> InstantTransmissionClientState.setCooldownTicks(pkt.cooldownTicks()));
    }
}
