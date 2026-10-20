package com.hmc.zenkai.feature.sense;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.LockOnClientState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * S2C, solo a la VÍCTIMA: "este jugador te ha fijado" (on) o "ya no te tiene fijado" (off). Antes
 * el único aviso era un texto en la actionbar; con esto el cliente puede dibujar una flecha roja
 * discreta hacia quien te fija (LockOnMarkerRenderer). La decisión de avisar (solo si la víctima
 * tiene el sentir el ki encendido) la toma el servidor en LockOnPacket; el "off" se manda siempre
 * porque quitar un aviso nunca revela nada.
 */
public record LockOnAlertPacket(int attackerId, boolean on) implements CustomPacketPayload {

    public static final Type<LockOnAlertPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "lock_on_alert"));

    public static final StreamCodec<FriendlyByteBuf, LockOnAlertPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, LockOnAlertPacket::attackerId,
                    ByteBufCodecs.BOOL, LockOnAlertPacket::on,
                    LockOnAlertPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(LockOnAlertPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> LockOnClientState.setAlert(pkt.attackerId(), pkt.on()));
    }
}
