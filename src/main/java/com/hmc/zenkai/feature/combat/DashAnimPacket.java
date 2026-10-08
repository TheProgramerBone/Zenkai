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
 * S2C, al propio jugador Y a quien lo rastrea: el servidor ejecutó un dash o un Paso rápido y
 * todos tienen que ver el mismo clip (PAL no replica animaciones custom por sí solo, mismo
 * motivo que UiDodgeAnimPacket). Se manda desde DashServer, que es quien decide si el gesto fue
 * dash normal o Paso rápido (el cliente no lo elige).
 */
public record DashAnimPacket(int entityId, int dir, boolean step) implements CustomPacketPayload {

    public static final Type<DashAnimPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "dash_anim"));

    public static final StreamCodec<FriendlyByteBuf, DashAnimPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, DashAnimPacket::entityId,
                    ByteBufCodecs.VAR_INT, DashAnimPacket::dir,
                    ByteBufCodecs.BOOL, DashAnimPacket::step,
                    DashAnimPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(DashAnimPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            var level = Minecraft.getInstance().level;
            if (level == null) return;
            if (level.getEntity(pkt.entityId()) instanceof AbstractClientPlayer p) {
                ZenkaiPalAnimations.playDash(p, pkt.dir(), pkt.step());
                if (pkt.step()) com.hmc.zenkai.client.AfterImageClient.spawn(p);
            }
        });
    }
}
