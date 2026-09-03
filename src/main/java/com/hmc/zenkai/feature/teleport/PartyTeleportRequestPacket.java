package com.hmc.zenkai.feature.teleport;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.event.tick.InstantTransmissionSystem;
import com.hmc.zenkai.feature.party.PartyManager;
import com.hmc.zenkai.feature.skills.SkillEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/**
 * C2S: el jugador eligió a un compañero de party en el menú de planetas (nivel 8+, ver
 * SkillEffects.instantTransmissionPartyUnlocked) — teletransporte a su posición EN VIVO, no a un
 * ancla fija (a diferencia de todos los demás destinos de Instant Transmission, ver
 * TeleportAnchors). El servidor revalida TODO de nuevo: nivel, que el destino siga siendo
 * compañero de party AHORA MISMO (pudo salir/ser expulsado entre que se abrió el menú y el
 * clic) y que siga conectado — un cliente modificado no puede pedir un TP a nadie que no sea de
 * su party ni saltarse el nivel mínimo.
 * No pasa por `requiresDiscovery()`/TeleportAnchors en absoluto: no hay nada que "descubrir" en
 * la posición de un jugador vivo, es lo opuesto al resto de destinos (fijos, conocidos de
 * antemano).
 */
public record PartyTeleportRequestPacket(UUID targetId) implements CustomPacketPayload {

    public static final Type<PartyTeleportRequestPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "party_teleport_request"));

    public static final StreamCodec<FriendlyByteBuf, PartyTeleportRequestPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, pkt) -> buf.writeUUID(pkt.targetId()),
                    buf -> new PartyTeleportRequestPacket(buf.readUUID()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PartyTeleportRequestPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            if (!SkillEffects.instantTransmissionPartyUnlocked(sp)) return;
            if (pkt.targetId().equals(sp.getUUID())) return; // no tiene sentido TP a uno mismo

            PartyManager.Party party = PartyManager.get(sp.server).partyOf(sp.getUUID());
            if (party == null || !party.members.contains(pkt.targetId())) return;

            ServerPlayer target = sp.server.getPlayerList().getPlayer(pkt.targetId());
            if (target == null) return; // desconectado ahora mismo

            InstantTransmissionAttachment att = InstantTransmissionAttachment.get(sp);
            ServerLevel destLevel = target.serverLevel();
            BlockPos targetPos = target.blockPosition();

            if (TeleportExecution.execute(sp, att, destLevel, targetPos)) {
                InstantTransmissionSystem.syncStateIfChanged(sp, att, true);
            }
        });
    }
}
