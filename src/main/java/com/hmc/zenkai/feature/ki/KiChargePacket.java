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

public record KiChargePacket(boolean charging) implements CustomPacketPayload {
    public static final Type<KiChargePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(
                    Zenkai.MOD_ID,
                    "ki_charge"
            ));

    public static final StreamCodec<FriendlyByteBuf, KiChargePacket> STREAM_CODEC =
            StreamCodec.of(KiChargePacket::encode, KiChargePacket::decode);

    public static void encode(FriendlyByteBuf buf, KiChargePacket pkt) {
        buf.writeBoolean(pkt.charging());
    }

    public static KiChargePacket decode(FriendlyByteBuf buf) {
        return new KiChargePacket(buf.readBoolean());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(KiChargePacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;

            PlayerStatsAttachment att = sp.getData(ZenkaiDataAttachments.PLAYER_STATS.get());
            att.setChargingKi(pkt.charging());
        });
    }
}