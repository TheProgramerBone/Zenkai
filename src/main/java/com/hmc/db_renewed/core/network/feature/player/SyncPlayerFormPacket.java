package com.hmc.db_renewed.core.network.feature.player;

import com.hmc.db_renewed.DragonBlockRenewed;
import com.hmc.db_renewed.core.network.feature.stats.DataAttachments;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SyncPlayerFormPacket(int entityId, CompoundTag data) implements CustomPacketPayload {

    public static final Type<SyncPlayerFormPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(DragonBlockRenewed.MOD_ID, "sync_player_form"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncPlayerFormPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, SyncPlayerFormPacket::entityId,
                    ByteBufCodecs.COMPOUND_TAG, SyncPlayerFormPacket::data,
                    SyncPlayerFormPacket::new
            );

    public static SyncPlayerFormPacket from(Player target) {
        var att = target.getData(DataAttachments.PLAYER_FORM.get());
        return new SyncPlayerFormPacket(target.getId(), att.save());
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SyncPlayerFormPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) return;

            Entity e = mc.level.getEntity(msg.entityId());
            if (!(e instanceof Player p)) return;

            var att = p.getData(DataAttachments.PLAYER_FORM.get());
            att.load(msg.data());
        });
    }
}
