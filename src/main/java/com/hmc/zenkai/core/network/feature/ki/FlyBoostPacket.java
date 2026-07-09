package com.hmc.zenkai.core.network.feature.ki;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.core.network.feature.stats.DataAttachments;
import com.hmc.zenkai.core.network.feature.player.PlayerStatsAttachment;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Bit cliente -> servidor: "estoy en boost de vuelo" (pose/hitbox horizontal, "acostado").
 * Edge-triggered desde el cliente (solo se envía al cambiar). El servidor lo usa en
 * TickHandlers para fijar Pose.SWIMMING (hitbox 0.6x0.6) mientras dure el boost.
 * Mismo patrón que KiChargePacket.
 */
public record FlyBoostPacket(boolean boosting) implements CustomPacketPayload {
    public static final Type<FlyBoostPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "fly_boost"));

    public static final StreamCodec<FriendlyByteBuf, FlyBoostPacket> STREAM_CODEC =
            StreamCodec.of(FlyBoostPacket::encode, FlyBoostPacket::decode);

    public static void encode(FriendlyByteBuf buf, FlyBoostPacket pkt) {
        buf.writeBoolean(pkt.boosting());
    }

    public static FlyBoostPacket decode(FriendlyByteBuf buf) {
        return new FlyBoostPacket(buf.readBoolean());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(FlyBoostPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            PlayerStatsAttachment att = sp.getData(DataAttachments.PLAYER_STATS.get());
            att.flags().setFlyBoosting(pkt.boosting());
        });
    }
}