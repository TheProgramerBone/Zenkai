package com.hmc.zenkai.core.network.feature.combat;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.core.network.feature.player.PlayerStatsAttachment;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C2S: el jugador entra/sale del modo combate (tecla X). Cosmético/estado: el servidor lo
 * guarda para nuevos trackers y lo re-difunde (la pose PAL por estilo la dibujan los
 * clientes en la Parte 2). El estilo viaja en el sync para que los clientes remotos no
 * dependan de tener el attachment del otro jugador.
 */
public record CombatModePacket(boolean active) implements CustomPacketPayload {

    public static final Type<CombatModePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "combat_mode"));

    public static final StreamCodec<FriendlyByteBuf, CombatModePacket> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.BOOL, CombatModePacket::active, CombatModePacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(CombatModePacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            if (pkt.active() && !PlayerStatsAttachment.get(sp).isRaceChosen()) return;
            CombatModeServerState.update(sp, pkt.active());
        });
    }
}