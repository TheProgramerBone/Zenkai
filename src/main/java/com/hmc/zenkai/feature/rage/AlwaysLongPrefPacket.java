package com.hmc.zenkai.feature.rage;

import com.hmc.zenkai.Zenkai;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * C2S: la preferencia «siempre la secuencia larga» del jugador (opción de su cliente). El servidor la apunta EN MEMORIA (no se guarda
 * en la partida: el cliente la reenvía al entrar) y {@link FirstTransformations} la usa para avisar con {@link LongTransformPacket}
 * en cada transformación suya, también cuando no es la primera vez, a él y a quienes lo ven. Es solo presentación: la protección de la
 * escena (invulnerable y sin acciones) sigue siendo exclusiva de la primera vez, para que esta opción no sea una invulnerabilidad repetible.
 * Un cliente que la mienta o la mande en bucle solo consigue que su propia transformación se vea larga: no afecta a nada más.
 */
public record AlwaysLongPrefPacket(boolean on) implements CustomPacketPayload {

    public static final Type<AlwaysLongPrefPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "always_long_pref"));

    public static final StreamCodec<FriendlyByteBuf, AlwaysLongPrefPacket> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> buf.writeBoolean(p.on()),
            buf -> new AlwaysLongPrefPacket(buf.readBoolean()));

    private static final Set<UUID> ON = ConcurrentHashMap.newKeySet();

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static boolean wants(UUID id) { return ON.contains(id); }

    public static void forget(UUID id) { ON.remove(id); }

    public static void handle(AlwaysLongPrefPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            if (pkt.on()) ON.add(sp.getUUID()); else ON.remove(sp.getUUID());
        });
    }
}
