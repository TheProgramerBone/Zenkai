package com.hmc.db_renewed.core.network.feature.race;

import com.hmc.db_renewed.DragonBlockRenewed;
import com.hmc.db_renewed.core.network.feature.player.PlayerLifeCycle;
import com.hmc.db_renewed.core.network.feature.player.PlayerVisualAttachment;
import com.hmc.db_renewed.core.network.feature.stats.DataAttachments;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record UpdatePlayerVisualPacket(CompoundTag data) implements CustomPacketPayload {

    public static final Type<UpdatePlayerVisualPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(DragonBlockRenewed.MOD_ID, "update_player_visual"));

    public static final StreamCodec<FriendlyByteBuf, UpdatePlayerVisualPacket> STREAM_CODEC =
            StreamCodec.of((buf, pkt) -> buf.writeNbt(pkt.data),
                    buf -> new UpdatePlayerVisualPacket(buf.readNbt()));

    public static UpdatePlayerVisualPacket from(PlayerVisualAttachment visual) {
        return new UpdatePlayerVisualPacket(visual.save());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(UpdatePlayerVisualPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (pkt.data == null) return;

            // Si lo recibe el SERVIDOR (cliente -> servidor)
            if (ctx.player() instanceof ServerPlayer sp) {
                var visual = sp.getData(DataAttachments.PLAYER_VISUAL.get());
                visual.load(pkt.data);

                // Re-broadcast a self + trackers (server -> clients)
                PlayerLifeCycle.syncVisualToTrackersAndSelf(sp);
                return;
            }

            // Si lo recibe el CLIENTE (server -> client)
            // ctx.player() aquí es LocalPlayer
            var p = ctx.player();

            var visual = p.getData(DataAttachments.PLAYER_VISUAL.get());

            visual.load(pkt.data);
        });
    }
}