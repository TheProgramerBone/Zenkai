package com.hmc.zenkai.feature.teleport;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.event.tick.InstantTransmissionSystem;
import com.hmc.zenkai.feature.skills.SkillEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C2S: el jugador eligió un destino en el menú de planetas (InstantTransmissionMenuScreen).
 * El servidor decide TODO de nuevo (nivel, descubrimiento, misma validación que ya hace el
 * cliente para pintar la fila habilitada/bloqueada) — un cliente modificado no puede pedir un
 * destino que no ha encontrado ni saltarse el nivel mínimo del menú.
 */
public record TeleportRequestPacket(String destinationId) implements CustomPacketPayload {

    public static final Type<TeleportRequestPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "teleport_request"));

    public static final StreamCodec<FriendlyByteBuf, TeleportRequestPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, pkt) -> buf.writeUtf(pkt.destinationId(), 32),
                    buf -> new TeleportRequestPacket(buf.readUtf(32)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(TeleportRequestPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            if (!SkillEffects.instantTransmissionMenuUnlocked(sp)) return;

            TeleportDestination dest = TeleportDestination.byId(pkt.destinationId());
            if (dest == null) return;
            // Dimensión bloqueada por datapack (ver InstantTransmissionBlocklist): la fila ya no
            // debería ni aparecer en el menú de un cliente sin modificar, pero se revalida aquí
            // igual — defensa en profundidad, mismo criterio que el resto de este handler.
            if (InstantTransmissionBlocklist.isBlocked(dest.realm().dimension())) return;

            InstantTransmissionAttachment att = InstantTransmissionAttachment.get(sp);
            if (!att.isDiscovered(dest)) return;

            boolean crossOk = SkillEffects.instantTransmissionCrossDimensionUnlocked(sp);
            if (!dest.executableThisPhase(sp.serverLevel().dimension(), crossOk)) return;

            ServerLevel destLevel = sp.server.getLevel(dest.realm().dimension());
            if (destLevel == null) return;

            BlockPos target = dest == TeleportDestination.HOME
                    ? resolveHome(sp, destLevel)
                    : TeleportAnchors.of(sp.server, dest);
            if (target == null) return;

            if (TeleportExecution.execute(sp, att, destLevel, target)) {
                // justTeleported=true por completitud (ver InstantTransmissionSystem.tryBlink) —
                // en la práctica el cliente ya habrá resuelto su espera de animación al abrir el
                // menú (ClientZenkaiPalTick.onInstantTransmissionMenuOpened), así que esto no
                // dispara nada nuevo ahí, solo mantiene la señal correcta para quien la lea.
                InstantTransmissionSystem.syncStateIfChanged(sp, att, true);
            }
        });
    }

    /** Home: el respawn del propio jugador, siempre en el Overworld (pedido explícito del
     *  usuario) — mismo patrón de resolución que OtherworldManager.revive(), forzando el
     *  Overworld en vez de confiar en getRespawnDimension() (que en la práctica ya suele
     *  serlo, las camas solo funcionan ahí, pero forzarlo es la garantía real). */
    private static BlockPos resolveHome(ServerPlayer sp, ServerLevel overworld) {
        BlockPos pos = net.minecraft.world.level.Level.OVERWORLD.equals(sp.getRespawnDimension())
                ? sp.getRespawnPosition() : null;
        return pos != null ? pos : overworld.getSharedSpawnPos();
    }
}
