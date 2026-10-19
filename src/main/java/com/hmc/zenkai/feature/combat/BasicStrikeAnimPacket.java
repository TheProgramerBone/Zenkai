package com.hmc.zenkai.feature.combat;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.event.ZenkaiPalAnimations;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** S2C, a quien rastrea al jugador (no a él: ya lo animó por predicción): reproduce el clip de golpe básico. */
public record BasicStrikeAnimPacket(int entityId, int variant) implements CustomPacketPayload {

    public static final Type<BasicStrikeAnimPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "basic_strike_anim"));

    public static final StreamCodec<FriendlyByteBuf, BasicStrikeAnimPacket> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.entityId());
                buf.writeVarInt(p.variant());
            },
            buf -> new BasicStrikeAnimPacket(buf.readVarInt(), buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(BasicStrikeAnimPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            var mc = Minecraft.getInstance();
            if (mc.level == null) return;
            if (mc.level.getEntity(pkt.entityId()) instanceof AbstractClientPlayer p && p != mc.player) {
                ZenkaiPalAnimations.playBasicStrike(p, pkt.variant());
            }
        });
    }
}
