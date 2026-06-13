package com.hmc.zenkai.core.network.feature.stats;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.core.ModGameRules;
import com.hmc.zenkai.core.network.feature.Race;
import com.hmc.zenkai.core.network.feature.player.PlayerLifeCycle;
import com.hmc.zenkai.core.network.feature.player.PlayerStatsAttachment;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;


public record ChooseRacePacket(Race race) implements CustomPacketPayload {

    public static final Type<ChooseRacePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "choose_race"));

    public static final StreamCodec<FriendlyByteBuf, ChooseRacePacket> STREAM_CODEC =
            StreamCodec.of(ChooseRacePacket::encode, ChooseRacePacket::decode);

    public static void encode(FriendlyByteBuf buf, ChooseRacePacket pkt) {
        buf.writeEnum(pkt.race());
    }

    public static ChooseRacePacket decode(FriendlyByteBuf buf) {
        return new ChooseRacePacket(buf.readEnum(Race.class));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ChooseRacePacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;

            // Gamerule: si la selección de raza está desactivada, rechazar silenciosamente
            // y notificar al jugador.
            if (!ModGameRules.allowRaceSelection(sp.server)) {
                sp.displayClientMessage(
                        Component.translatable("messages.zenkai.race_selection_disabled"),
                        true
                );
                return;
            }

            PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
            att.setRace(pkt.race());
            att.setRaceChosen(true);
            att.setStyleChosen(false);
            PlayerLifeCycle.sync(sp);
            PlayerLifeCycle.syncVisualToTrackersAndSelf(sp);
        });
    }
}