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
 * C2S: devolver N puntos de un atributo y recuperar la parte proporcional del TP.
 * LA CANTIDAD VIAJA EN EL PAQUETE Y EL BUCLE VIVE EN EL SERVIDOR. El reembolso depende de la
 * posición de cada punto en la curva de coste, así que devolver cien puntos son cien pasos
 * distintos, no cien veces el mismo precio. Se podría haber mandado un paquete por punto, pero
 * eso son cien mensajes por clic con el multiplicador alto: la red se llena de tráfico para una
 * operación que el servidor resuelve en un bucle de cien iteraciones.
 * El TOPE existe porque la cantidad la elige el cliente: un paquete fabricado a mano con
 * Integer.MAX_VALUE haría girar el bucle dos mil millones de veces dentro del hilo del
 * servidor. El tope coincide con el paso mayor del selector, así que el juego legítimo nunca lo
 * toca.
 * Devolver MENOS de lo pedido es correcto y no es un error: si el jugador pide cien y solo
 * tiene cuarenta invertidos, se le devuelven cuarenta. La alternativa —rechazar el paquete
 * entero— obligaría al cliente a saber exactamente cuántos puntos hay antes de pulsar, y ese
 * dato puede estar un tick desfasado.
 */
public record RefundTpPacket(String attrName, int points) implements CustomPacketPayload {

    /** Coincide con el paso mayor del selector de la pantalla de stats. */
    public static final int MAX_POINTS = 100_000;

    public static final Type<RefundTpPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "refund_tp"));

    public static final StreamCodec<FriendlyByteBuf, RefundTpPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, pkt) -> {
                        buf.writeUtf(pkt.attrName(), 32);
                        buf.writeVarInt(pkt.points());
                    },
                    buf -> new RefundTpPacket(buf.readUtf(32), buf.readVarInt()));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(RefundTpPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            var sp = ctx.player();

            ZenkaiAttributes a;
            try { a = ZenkaiAttributes.fromString(pkt.attrName()); } catch (Exception ignored) { return; }

            int n = Math.min(Math.max(0, pkt.points()), MAX_POINTS);
            if (n <= 0) return;

            var att = sp.getData(ZenkaiDataAttachments.PLAYER_STATS.get());
            // El servidor decide cuántos se devuelven de verdad: refundPoints se para en cuanto
            // ese atributo se queda sin puntos INVERTIDOS (los de base racial no se venden).
            if (att.refundPoints(a, n) > 0) {
                PlayerLifeCycle.sync((ServerPlayer) sp);
            }
        });
    }
}