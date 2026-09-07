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
 * S2C, a TRACKERS (nunca al propio jugador: el local ya anima por predicción de su propia
 * tecla, ver ClientZenkaiPalTick — mandárselo también solo relanzaría la misma pose a mitad).
 * Avisa a quien está viendo a este jugador que sostiene TAB (o que ha dejado de hacerlo), para
 * que su pose de carga de Transmisión Instantánea se vea igual desde fuera que en primera
 * persona.
 * Antes de esto, InstantTransmissionSyncPacket (self-only, cooldown+quietud) era el ÚNICO
 * paquete de esta técnica, así que ningún observador veía nunca el brazo alzado — el mismo
 * hueco que ya cubren KiChargeStatePacket para técnicas ki y ActionState para físicas
 * (ver ClientZenkaiPalTick.tickPhysAnim), que Instant Transmission nunca llegó a compartir por
 * quedar fuera de ActionRules a propósito (ver el javadoc de clase de InstantTransmissionSystem).
 * Servidor-autoritativo: se manda desde InstantTransmissionSystem.tick() (el mismo sitio que ya
 * calcula holding/wasHolding) y desde CombatModeServerState.onStartTracking-equivalente aquí
 * mismo, para que quien empieza a trackear a mitad de una carga la vea desde el primer frame.
 */
public record InstantTransmissionAnimPacket(int entityId, boolean charging) implements CustomPacketPayload {

    public static final Type<InstantTransmissionAnimPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "instant_transmission_anim"));

    public static final StreamCodec<FriendlyByteBuf, InstantTransmissionAnimPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, InstantTransmissionAnimPacket::entityId,
                    ByteBufCodecs.BOOL, InstantTransmissionAnimPacket::charging,
                    InstantTransmissionAnimPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(InstantTransmissionAnimPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() ->
                InstantTransmissionClientState.setRemoteCharging(pkt.entityId(), pkt.charging()));
    }
}
