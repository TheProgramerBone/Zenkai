package com.hmc.db_renewed.core.network;

import com.hmc.db_renewed.DragonBlockRenewed;
import com.hmc.db_renewed.core.network.feature.player.PlayerLifeCycle;
import com.hmc.db_renewed.core.network.feature.player.PlayerStatsAttachment;
import com.hmc.db_renewed.core.network.feature.Style;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ChooseStylePacket(Style style) implements CustomPacketPayload {

    public static final Type<ChooseStylePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(
                    DragonBlockRenewed.MOD_ID,
                    "choose_style"
            ));

    // Igual que en ChooseRacePacket: codec manual con writeEnum/readEnum
    public static final StreamCodec<FriendlyByteBuf, ChooseStylePacket> STREAM_CODEC =
            StreamCodec.of(ChooseStylePacket::encode, ChooseStylePacket::decode);

    // --- codec ---

    public static void encode(FriendlyByteBuf buf, ChooseStylePacket pkt) {
        buf.writeEnum(pkt.style());
    }

    public static ChooseStylePacket decode(FriendlyByteBuf buf) {
        Style style = buf.readEnum(Style.class);
        return new ChooseStylePacket(style);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    // --- handler ---

    public static void handle(ChooseStylePacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof net.minecraft.server.level.ServerPlayer sp)) return;

            PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
            att.setStyle(pkt.style());
            att.setStyleChosen(true);
            PlayerLifeCycle.sync(sp);
        });
    }
}