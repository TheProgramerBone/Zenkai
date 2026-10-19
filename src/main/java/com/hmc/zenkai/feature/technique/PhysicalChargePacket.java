package com.hmc.zenkai.feature.technique;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.feature.combat.CombatModeServerState;
import com.hmc.zenkai.feature.combat.StunState;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * C2S: el jugador empieza (true) o abandona (false) la carga de HEAVY_BLOW. SOLO avisa del momento: el
 * nivel de carga lo calcula el servidor con su propio reloj al llegar el PhysicalFirePacket, así que
 * un cliente tramposo no puede declarar un nivel. Soltar con éxito no necesita este packet: el
 * PhysicalFirePacket consume la carga.
 */
public record PhysicalChargePacket(boolean start) implements CustomPacketPayload {

    public static final Type<PhysicalChargePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "physical_charge"));

    public static final StreamCodec<FriendlyByteBuf, PhysicalChargePacket> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.BOOL, PhysicalChargePacket::start,
                    PhysicalChargePacket::new);

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(PhysicalChargePacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            if (!pkt.start()) {
                PhysicalCombatServer.cancelCharge(sp.getUUID());
                return;
            }
            // Mismas condiciones de fondo que el golpe: sin ellas no se arma ninguna carga.
            if (!CombatModeServerState.isActive(sp.getUUID()) || StunState.isStunned(sp)) return;
            if (!PlayerStatsAttachment.get(sp).techniques().isUnlocked(PhysicalTechnique.HEAVY_BLOW)) return;
            PhysicalCombatServer.beginCharge(sp);
        });
    }
}
