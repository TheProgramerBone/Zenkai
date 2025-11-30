package com.hmc.db_renewed.network.ki;

import com.hmc.db_renewed.DragonBlockRenewed;
import com.hmc.db_renewed.network.stats.DataAttachments;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ToggleFlyPacket() implements CustomPacketPayload {
    public static final Type<ToggleFlyPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(DragonBlockRenewed.MOD_ID, "toggle_fly"));

    public static final StreamCodec<FriendlyByteBuf, ToggleFlyPacket> STREAM_CODEC =
            StreamCodec.of((buf, pkt) -> {}, buf -> new ToggleFlyPacket());

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(ToggleFlyPacket pkt, IPayloadContext ctx) {
        var sp = ctx.player();
        ctx.enqueueWork(() -> {
            var att = sp.getData(DataAttachments.PLAYER_STATS.get());
            att.setFlyEnabled(!att.isFlyEnabled()); // toggle
            sp.onUpdateAbilities(); // sincroniza abilities al cliente
        });
    }
}