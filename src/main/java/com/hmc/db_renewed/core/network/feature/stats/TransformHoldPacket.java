package com.hmc.db_renewed.core.network.feature.stats;

import com.hmc.db_renewed.DragonBlockRenewed;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

public record TransformHoldPacket(boolean transforming) implements CustomPacketPayload {

    public static final Type<TransformHoldPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(DragonBlockRenewed.MOD_ID, "transform_hold"));

    public static final StreamCodec<FriendlyByteBuf, TransformHoldPacket> STREAM_CODEC =
            StreamCodec.of(TransformHoldPacket::encode, TransformHoldPacket::decode);

    public static void encode(FriendlyByteBuf buf, TransformHoldPacket pkt) {
        buf.writeBoolean(pkt.transforming());
    }

    public static TransformHoldPacket decode(FriendlyByteBuf buf) {
        return new TransformHoldPacket(buf.readBoolean());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(TransformHoldPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;

            PlayerStatsAttachment att = sp.getData(DataAttachments.PLAYER_STATS.get());

            // Gate: si no eligió raza, nunca puede transformarse
            if (!att.isRaceChosen()) {
                if (att.isTransforming()) {
                    att.setTransforming(false);
                }
                PlayerLifeCycle.sync(sp);
                return;
            }

            // Aplicar estado
            att.setTransforming(pkt.transforming());

            // Sync inmediato a self + trackers (tu método sync() ya hace ambos)
            PlayerLifeCycle.sync(sp);
        });
    }

}