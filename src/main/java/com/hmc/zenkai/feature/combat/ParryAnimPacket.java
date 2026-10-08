package com.hmc.zenkai.feature.combat;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.event.ZenkaiPalAnimations;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * S2C, al propio jugador y a quien lo rastrea: el servidor resolvió un parry y todos tienen que ver
 * el mismo clip (PAL no replica animaciones custom por sí solo, mismo motivo que DashAnimPacket).
 */
public record ParryAnimPacket(int entityId) implements CustomPacketPayload {

    public static final Type<ParryAnimPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "parry_anim"));

    public static final StreamCodec<FriendlyByteBuf, ParryAnimPacket> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, ParryAnimPacket::entityId,
                    ParryAnimPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(ParryAnimPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            var level = Minecraft.getInstance().level;
            if (level == null) return;
            if (level.getEntity(pkt.entityId()) instanceof AbstractClientPlayer p) {
                ZenkaiPalAnimations.playParry(p);
            }
        });
    }
}
