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
 * Bit cliente -> servidor: "sostengo Shift + la tecla de cargar ki" (intento de forzar el
 * powerPercent por encima de 100%). Edge-triggered desde el cliente (solo se envía al cambiar),
 * mismo patrón que FlyBoostPacket/KiChargePacket. El servidor solo espeja el flag
 * (PlayerStateFlags.overdriveCharging); la lógica de qué hacer con él (temblor, rotura del
 * límite, drenaje) vive en KiChargeSystem/OverdriveSystem, no aquí.
 */
public record OverdriveChargePacket(boolean forcing) implements CustomPacketPayload {
    public static final Type<OverdriveChargePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "overdrive_charge"));

    public static final StreamCodec<FriendlyByteBuf, OverdriveChargePacket> STREAM_CODEC =
            StreamCodec.of(OverdriveChargePacket::encode, OverdriveChargePacket::decode);

    public static void encode(FriendlyByteBuf buf, OverdriveChargePacket pkt) {
        buf.writeBoolean(pkt.forcing());
    }

    public static OverdriveChargePacket decode(FriendlyByteBuf buf) {
        return new OverdriveChargePacket(buf.readBoolean());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OverdriveChargePacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            PlayerStatsAttachment att = sp.getData(ZenkaiDataAttachments.PLAYER_STATS.get());
            att.flags().setOverdriveCharging(pkt.forcing());
        });
    }
}
