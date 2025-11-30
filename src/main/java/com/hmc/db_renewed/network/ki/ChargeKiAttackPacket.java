package com.hmc.db_renewed.network.ki;

import com.hmc.db_renewed.DragonBlockRenewed;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final Logger LOGGER = LoggerFactory.getLogger("DBR-KiAttack");

    public static void handle(ChargeKiAttackPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;

            // DEBUG SERVIDOR (handler de paquete)
            sp.sendSystemMessage(Component.literal("[SERVER] ChargeKiAttackPacket start=" + pkt.start()));
            LOGGER.info("[SERVER] ChargeKiAttackPacket start={} for {}", pkt.start(), sp.getGameProfile().getName());

            if (pkt.start()) {
                KiAttackServerLogic.startCharging(sp);
            } else {
                KiAttackServerLogic.releaseCharging(sp);
            }
        });
    }
}