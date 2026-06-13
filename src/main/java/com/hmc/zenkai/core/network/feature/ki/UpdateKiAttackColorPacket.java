package com.hmc.zenkai.core.network.feature.ki;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.core.network.feature.stats.DataAttachments;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record UpdateKiAttackColorPacket(String attackId, int rgb) implements CustomPacketPayload {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID,"update_ki_attack_color");
    public static final Type<UpdateKiAttackColorPacket> TYPE = new Type<>(ID);

    public static final StreamCodec<FriendlyByteBuf, UpdateKiAttackColorPacket> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, UpdateKiAttackColorPacket::attackId,
                    ByteBufCodecs.INT,         UpdateKiAttackColorPacket::rgb,
                    UpdateKiAttackColorPacket::new
            );

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(UpdateKiAttackColorPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            var p = ctx.player();
            var att = p.getData(DataAttachments.PLAYER_STATS.get());
            var def = att.getKiAttack(pkt.attackId());
            return;
        });
    }
}