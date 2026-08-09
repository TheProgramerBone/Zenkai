package com.hmc.zenkai.feature.stats;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.feature.ZenkaiAttributes;
import com.hmc.zenkai.feature.player.PlayerLifeCycle;
import com.hmc.zenkai.registry.ZenkaiDataAttachments;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C2S: devolver UN punto de atributo y recuperar la parte proporcional del TP.
 * Sin cantidad en el paquete, a propósito. El reembolso depende de la posición del punto en la
 * curva de coste, así que devolver N puntos no es devolver uno N veces con el mismo precio: es
 * N pasos distintos. Aceptar una cantidad obligaría a repetir aquí ese bucle y a decidir qué
 * hacer si a la mitad deja de haber puntos que devolver. Un punto por paquete deja toda esa
 * lógica en un único sitio (PlayerRaceStats#refundPoint) y hace el paquete idempotente de
 * hecho: si llega duplicado, devuelve otro punto que el jugador tiene, y ya está.
 * NO existe un "devolver entero": eso es el respec, y el respec no está al alcance del jugador
 * por diseño (lo conceden maestros y el comando de operador).
 */
public record RefundTpPacket(String attrName) implements CustomPacketPayload {

    public static final Type<RefundTpPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "refund_tp"));

    public static final StreamCodec<FriendlyByteBuf, RefundTpPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, pkt) -> buf.writeUtf(pkt.attrName(), 32),
                    buf -> new RefundTpPacket(buf.readUtf(32)));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(RefundTpPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            var sp = ctx.player();

            ZenkaiAttributes a;
            try { a = ZenkaiAttributes.fromString(pkt.attrName()); } catch (Exception ignored) { return; }

            var att = sp.getData(ZenkaiDataAttachments.PLAYER_STATS.get());
            // El servidor decide: refundPoint devuelve -1 si ese atributo no tiene puntos
            // INVERTIDOS (los de base racial no se pueden vender). El cliente ya apaga el
            // botón en ese caso, pero un paquete se puede fabricar a mano.
            if (att.refundPoint(a) >= 0) {
                PlayerLifeCycle.sync((ServerPlayer) sp);
            }
        });
    }
}