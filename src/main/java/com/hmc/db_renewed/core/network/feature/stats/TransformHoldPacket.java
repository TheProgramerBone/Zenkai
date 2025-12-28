package com.hmc.db_renewed.core.network.feature.stats;

import com.hmc.db_renewed.DragonBlockRenewed;
import com.hmc.db_renewed.core.network.feature.player.PlayerLifeCycle;
import com.hmc.db_renewed.core.network.feature.player.PlayerStatsAttachment;
import com.hmc.db_renewed.core.network.feature.race.forms.FormIds;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record TransformHoldPacket(Action action, boolean active) implements CustomPacketPayload {

    public enum Action {
        TRANSFORM_HOLD, // ALT + C (hold)
        DETRANSFORM     // SHIFT + C (tap o hold, da igual)
    }

    public static final Type<TransformHoldPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(DragonBlockRenewed.MOD_ID, "transform_hold"));

    public static final StreamCodec<FriendlyByteBuf, TransformHoldPacket> STREAM_CODEC =
            StreamCodec.of(TransformHoldPacket::encode, TransformHoldPacket::decode);

    private static void encode(FriendlyByteBuf buf, TransformHoldPacket pkt) {
        buf.writeEnum(pkt.action());
        buf.writeBoolean(pkt.active());
    }

    private static TransformHoldPacket decode(FriendlyByteBuf buf) {
        Action a = buf.readEnum(Action.class);
        boolean v = buf.readBoolean();
        return new TransformHoldPacket(a, v);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(TransformHoldPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;

            var stats  = sp.getData(DataAttachments.PLAYER_STATS.get());
            var form   = sp.getData(DataAttachments.PLAYER_FORM.get());

            if (!stats.isRaceChosen()) {
                form.resetAll();
                PlayerLifeCycle.syncFormToTrackersAndSelf(sp);
                return;
            }

            if (pkt.action() == Action.DETRANSFORM) {
                // Solo si está transformado
                if (!FormIds.BASE.equals(form.getFormId())) {
                    form.forceBase(); // lo creamos abajo
                }
                PlayerLifeCycle.syncFormToTrackersAndSelf(sp);
                return;
            }

            // Acción normal: hold transformación (ALT + C)
            form.setTransformHeld(pkt.active());
            PlayerLifeCycle.syncFormToTrackersAndSelf(sp);
        });
    }
}
