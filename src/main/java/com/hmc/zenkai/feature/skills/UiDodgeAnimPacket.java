package com.hmc.zenkai.feature.skills;

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
 * S2C, al propio jugador Y a quien lo rastrea: el servidor decide el esquive automático de
 * Ultra Instinct (OverlayMetersSystem.tryDodge) y elige el gesto según el golpe que llega
 * (UiDodgeChoice; variant = una de sus constantes), así todos ven el mismo. PAL no replica
 * animaciones custom por sí solo (mismo motivo que InstantTransmissionAnimPacket).
 */
public record UiDodgeAnimPacket(int entityId, int variant) implements CustomPacketPayload {

    public static final Type<UiDodgeAnimPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "ui_dodge_anim"));

    public static final StreamCodec<FriendlyByteBuf, UiDodgeAnimPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, UiDodgeAnimPacket::entityId,
                    ByteBufCodecs.VAR_INT, UiDodgeAnimPacket::variant,
                    UiDodgeAnimPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(UiDodgeAnimPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            var level = Minecraft.getInstance().level;
            if (level == null) return;
            if (level.getEntity(pkt.entityId()) instanceof AbstractClientPlayer p) {
                ZenkaiPalAnimations.playUiDodge(p, pkt.variant());
            }
        });
    }
}
