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
 * S2C, a TRACKERS, PULSO de un solo tick: "este jugador acaba de blinkear de verdad" — el
 * equivalente para observadores del justTeleported=true de InstantTransmissionSyncPacket
 * (self-only). Sin esto, un tracker veía la pose de carga cortarse en seco (o quedarse
 * congelada) en vez del swing de salida (el brazo baja solo) que sí ve el propio jugador.
 * Mandado desde TeleportExecution.execute (la cola compartida de CUALQUIER salto de Instant
 * Transmission — blink de nivel 1 y menú de planetas por igual) justo ANTES de teletransportar,
 * mientras los trackers de la posición de ORIGEN todavía siguen a la entidad.
 */
public record InstantTransmissionReleaseAnimPacket(int entityId) implements CustomPacketPayload {

    public static final Type<InstantTransmissionReleaseAnimPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "instant_transmission_release_anim"));

    public static final StreamCodec<FriendlyByteBuf, InstantTransmissionReleaseAnimPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, InstantTransmissionReleaseAnimPacket::entityId,
                    InstantTransmissionReleaseAnimPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(InstantTransmissionReleaseAnimPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> InstantTransmissionClientState.markRemoteReleased(pkt.entityId()));
    }
}
