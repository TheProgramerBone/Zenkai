package com.hmc.zenkai.core.network.feature.player;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.gui.screens.RaceSelectionScreen;
import com.hmc.zenkai.core.network.feature.stats.DataAttachments;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SyncPlayerStatsPacket(CompoundTag data) implements CustomPacketPayload {
    public static final Type<SyncPlayerStatsPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "sync_player_stats"));

    public static final StreamCodec<FriendlyByteBuf, SyncPlayerStatsPacket> STREAM_CODEC =
            StreamCodec.of((buf, pkt) -> buf.writeNbt(pkt.data),
                    (buf) -> new SyncPlayerStatsPacket(buf.readNbt()));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    // Cliente: aplicar
    public static void handle(SyncPlayerStatsPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            var mc = Minecraft.getInstance();
            if (mc.player == null || pkt.data == null) return;
            PlayerStatsAttachment att = mc.player.getData(DataAttachments.PLAYER_STATS.get());
            if (mc.player == null) return;
            if (mc.screen instanceof RaceSelectionScreen) {
                return;
            }
            att.load(pkt.data);
        });
    }

    public static SyncPlayerStatsPacket from(PlayerStatsAttachment att) {
        return new SyncPlayerStatsPacket(att.save());
    }
}