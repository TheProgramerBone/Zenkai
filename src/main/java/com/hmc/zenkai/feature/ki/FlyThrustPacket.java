package com.hmc.zenkai.feature.ki;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.registry.ZenkaiDataAttachments;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Bit cliente -> servidor: "estoy empujando el vuelo" (adelante pulsado o crucero activo).
 * Edge-triggered desde el cliente, mismo patrón que FlyBoostPacket. El servidor no puede leer
 * esto del input: un jugador que no va montado no manda ServerboundPlayerInputPacket, así que
 * `zza` vale siempre 0 en el servidor. Lo usa FlightSystem para subir o bajar la rampa de
 * velocidad; es solo un hint de ritmo (no concede velocidad por sí mismo: el techo sigue
 * saliendo de la skill Fly y del poder), así que mentir con él no da ventaja.
 */
public record FlyThrustPacket(boolean thrust) implements CustomPacketPayload {
    public static final Type<FlyThrustPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "fly_thrust"));

    public static final StreamCodec<FriendlyByteBuf, FlyThrustPacket> STREAM_CODEC =
            StreamCodec.of(FlyThrustPacket::encode, FlyThrustPacket::decode);

    public static void encode(FriendlyByteBuf buf, FlyThrustPacket pkt) {
        buf.writeBoolean(pkt.thrust());
    }

    public static FlyThrustPacket decode(FriendlyByteBuf buf) {
        return new FlyThrustPacket(buf.readBoolean());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(FlyThrustPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            PlayerStatsAttachment att = sp.getData(ZenkaiDataAttachments.PLAYER_STATS.get());
            att.flags().setFlyThrust(pkt.thrust());
        });
    }
}
