package com.hmc.zenkai.feature.ki;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.feature.player.PlayerLifeCycle;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.feature.skills.SkillEffects;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C2S: bajar el % de poder en uso 5 puntos (tecla Z). Server clampa a 50.
 * Ya no manda mensaje de action bar: el cascarón circular del HUD (KiChargeGaugeOverlay) hace
 * un flash corto al detectar el cambio de powerPercent en el propio sync, así que el aviso de
 * texto aparte solo repetía la misma información con más parpadeo.
 */
public record PowerPercentPacket() implements CustomPacketPayload {

    public static final Type<PowerPercentPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "power_percent_down"));

    public static final StreamCodec<FriendlyByteBuf, PowerPercentPacket> STREAM_CODEC =
            StreamCodec.of((buf, pkt) -> {}, buf -> new PowerPercentPacket());

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(PowerPercentPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
            if (!att.isRaceChosen()) return;
            if (att.setPowerPercent(att.getPowerPercent() - 5, SkillEffects.maxPowerPercent(sp))) {
                PlayerLifeCycle.syncIfServer(sp);
            }
        });
    }
}