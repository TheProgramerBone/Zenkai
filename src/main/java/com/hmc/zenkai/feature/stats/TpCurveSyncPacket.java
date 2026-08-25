package com.hmc.zenkai.feature.stats;

import com.hmc.zenkai.Zenkai;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * S2C: los dos números de la curva de coste de atributos (login y /reload).
 * Existe porque CommonConfig no viaja al cliente y la pantalla de stats necesita enseñar el
 * precio ANTES de mandar el packet de compra. Ver TpCurve para el detalle.
 * Lo envía SkillManager#onDatapackSync, que ya corre en login y en /reload y ya sabe
 * distinguir "un jugador" de "el conjunto". Montar un segundo listener para dos doubles habría
 * duplicado esa lógica de reparto.
 */
public record TpCurveSyncPacket(double base, double coeff) implements CustomPacketPayload {

    public static final Type<TpCurveSyncPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "tp_curve_sync"));

    public static final StreamCodec<FriendlyByteBuf, TpCurveSyncPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.DOUBLE, TpCurveSyncPacket::base,
                    ByteBufCodecs.DOUBLE, TpCurveSyncPacket::coeff,
                    TpCurveSyncPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(TpCurveSyncPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> TpCurve.adopt(pkt.base(), pkt.coeff()));
    }
}