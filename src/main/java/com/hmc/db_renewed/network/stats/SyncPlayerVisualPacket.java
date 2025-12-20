package com.hmc.db_renewed.network.stats;

import com.hmc.db_renewed.DragonBlockRenewed;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SyncPlayerVisualPacket(int entityId, CompoundTag data) implements CustomPacketPayload {

    public static final Type<SyncPlayerVisualPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(DragonBlockRenewed.MOD_ID, "sync_player_visual"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncPlayerVisualPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, SyncPlayerVisualPacket::entityId,
                    ByteBufCodecs.COMPOUND_TAG, SyncPlayerVisualPacket::data,
                    SyncPlayerVisualPacket::new
            );

    public static SyncPlayerVisualPacket from(Player target) {
        PlayerVisualAttachment att = target.getData(DataAttachments.PLAYER_VISUAL.get());
        return new SyncPlayerVisualPacket(target.getId(), att.save());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SyncPlayerVisualPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> applyClient(msg));
    }

    @OnlyIn(Dist.CLIENT)
    private static void applyClient(SyncPlayerVisualPacket msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        Entity e = mc.level.getEntity(msg.entityId());
        if (!(e instanceof Player p)) return;

        PlayerVisualAttachment att = p.getData(DataAttachments.PLAYER_VISUAL.get());
        att.load(msg.data());
    }
}