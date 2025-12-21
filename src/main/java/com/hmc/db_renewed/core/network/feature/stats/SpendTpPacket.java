package com.hmc.db_renewed.core.network.feature.stats;

import com.hmc.db_renewed.DragonBlockRenewed;
import com.hmc.db_renewed.core.network.feature.Dbrattributes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SpendTpPacket(String attrName, int points) implements CustomPacketPayload {
    public static final Type<SpendTpPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(DragonBlockRenewed.MOD_ID, "spend_tp"));

    public static final StreamCodec<FriendlyByteBuf, SpendTpPacket> STREAM_CODEC =
            StreamCodec.of((buf, pkt) -> {
                buf.writeUtf(pkt.attrName(), 32); // máx 32 chars
                buf.writeVarInt(pkt.points());
            }, buf -> {
                String attr = buf.readUtf(32);
                int pts = buf.readVarInt();
                return new SpendTpPacket(attr, pts);
            });

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SpendTpPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            var sp = ctx.player();

            Dbrattributes a;
            try { a = Dbrattributes.fromString(pkt.attrName()); } catch (Exception ignored) { return; }

            var att = sp.getData(DataAttachments.PLAYER_STATS.get());
            if (pkt.points() > 0 && att.spendTP(a, pkt.points())) {
                PlayerLifeCycle.sync((ServerPlayer) sp);
            }
        });
    }
}