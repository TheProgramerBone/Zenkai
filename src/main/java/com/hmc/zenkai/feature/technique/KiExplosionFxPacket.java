package com.hmc.zenkai.feature.technique;

import com.hmc.zenkai.Zenkai;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * S2C, a quien esté cerca: la técnica EXPLOSION acaba de detonar en ese punto. El cliente dibuja la detonación a su tamaño REAL
 * (cúpula de luz, ondas de suelo, polvo y lo que queda después; ver {@code client/fx/ExplosionBlastFx}). Antes la detonación era
 * un único destello de partícula con tope de tamaño, que no llegaba ni de lejos al radio que hace daño.
 * Es presentación pura: el daño ya lo repartió el servidor en {@code KiProjectileEntity.detonate}.
 */
public record KiExplosionFxPacket(double x, double y, double z, float radius, int rgb) implements CustomPacketPayload {

    public static final Type<KiExplosionFxPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "ki_explosion_fx"));

    public static final StreamCodec<FriendlyByteBuf, KiExplosionFxPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.DOUBLE, KiExplosionFxPacket::x,
                    ByteBufCodecs.DOUBLE, KiExplosionFxPacket::y,
                    ByteBufCodecs.DOUBLE, KiExplosionFxPacket::z,
                    ByteBufCodecs.FLOAT, KiExplosionFxPacket::radius,
                    ByteBufCodecs.INT, KiExplosionFxPacket::rgb,
                    KiExplosionFxPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(KiExplosionFxPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> com.hmc.zenkai.client.fx.ExplosionBlastFx.start(
                pkt.x(), pkt.y(), pkt.z(), pkt.radius(), pkt.rgb()));
    }
}
