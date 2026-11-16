package com.hmc.zenkai.feature.combat;

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
 * C2S: el jugador lanzó un golpe básico con la mano vacía en modo combate; {@code variant} es el clip que su cliente
 * ya reprodujo (0 puño de la mano principal, 1 puño de la otra, 2 puño, 3 patada). El servidor solo lo REDIFUNDE a quien
 * lo rastrea (BasicStrikeAnimPacket), porque PAL no replica animaciones custom por sí solo. No decide nada de
 * gameplay: es pura presentación, por eso basta una comprobación mínima (modo combate, variante válida y un tope de
 * frecuencia para que no se use de altavoz de paquetes).
 */
public record BasicStrikePacket(int variant) implements CustomPacketPayload {

    public static final Type<BasicStrikePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "basic_strike"));

    public static final StreamCodec<FriendlyByteBuf, BasicStrikePacket> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> buf.writeVarInt(p.variant()),
            buf -> new BasicStrikePacket(buf.readVarInt()));

    /** Último gameTime en que cada jugador mandó uno. */
    private static final Map<UUID, Long> LAST = new ConcurrentHashMap<>();
    private static final int MIN_GAP_TICKS = 3;

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(BasicStrikePacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            if (pkt.variant() < 0 || pkt.variant() > 3) return;
            if (!CombatModeServerState.isActive(sp.getUUID()) || StunState.isStunned(sp)) return;
            long now = sp.level().getGameTime();
            Long prev = LAST.put(sp.getUUID(), now);
            if (prev != null && now - prev < MIN_GAP_TICKS) return;
            // El esquive de Ultra Instinto del rival se coordina con el miembro que golpea.
            com.hmc.zenkai.feature.skills.UiDodgeChoice.noteBasicStrike(sp, pkt.variant());
            PacketDistributor.sendToPlayersTrackingEntity(sp, new BasicStrikeAnimPacket(sp.getId(), pkt.variant()));
        });
    }

    public static void forget(UUID id) { LAST.remove(id); }
}
