package com.hmc.zenkai.feature.rage;

import com.hmc.zenkai.Zenkai;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * C2S: el jugador abrió la pestaña de la Crónica y pide su historia. No lleva datos: el servidor responde SIEMPRE con la del
 * propio jugador (nunca la de otro), con {@link RageChronicleSyncPacket}. Bajo demanda y no en cada cambio, para no mandar
 * hasta 200 entradas cada vez que algo ocurre. Con un mínimo de {@link #MIN_GAP_TICKS} entre peticiones del mismo jugador:
 * un cliente que la pida en bucle no obtiene nada más que su propia respuesta, pero tampoco fuerza al servidor a serializarla.
 */
public record RageChronicleRequestPacket() implements CustomPacketPayload {

    public static final Type<RageChronicleRequestPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "rage_chronicle_request"));

    public static final StreamCodec<FriendlyByteBuf, RageChronicleRequestPacket> STREAM_CODEC =
            StreamCodec.unit(new RageChronicleRequestPacket());

    static final int MIN_GAP_TICKS = 10;
    private static final Map<UUID, Long> LAST = new ConcurrentHashMap<>();

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void forget(UUID id) { LAST.remove(id); }

    public static void handle(RageChronicleRequestPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            long now = sp.level().getGameTime();
            Long last = LAST.get(sp.getUUID());
            if (last != null && now - last < MIN_GAP_TICKS) return;
            LAST.put(sp.getUUID(), now);
            PacketDistributor.sendToPlayer(sp, new RageChronicleSyncPacket(RageChronicle.get(sp).entries()));
        });
    }
}
