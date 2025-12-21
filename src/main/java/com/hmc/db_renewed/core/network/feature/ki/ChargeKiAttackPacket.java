package com.hmc.db_renewed.core.network.feature.ki;

import com.hmc.db_renewed.DragonBlockRenewed;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ChargeKiAttackPacket(boolean start) implements CustomPacketPayload {

    public static final Type<ChargeKiAttackPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(
                    DragonBlockRenewed.MOD_ID,
                    "charge_ki_attack"
            ));

    public static final StreamCodec<FriendlyByteBuf, ChargeKiAttackPacket> STREAM_CODEC =
            StreamCodec.of(ChargeKiAttackPacket::encode, ChargeKiAttackPacket::decode);

    public static void encode(FriendlyByteBuf buf, ChargeKiAttackPacket pkt) {
        buf.writeBoolean(pkt.start());
    }

    public static ChargeKiAttackPacket decode(FriendlyByteBuf buf) {
        boolean start = buf.readBoolean();
        return new ChargeKiAttackPacket(start);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ChargeKiAttackPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;

            if (pkt.start()) {
                KiAttackServerLogic.startCharging(sp);
            } else {
                KiAttackServerLogic.releaseCharging(sp);
            }
        });
    }
}