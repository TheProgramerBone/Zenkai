package com.hmc.zenkai.feature.stats;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.registry.ZenkaiDataAttachments;
import com.hmc.zenkai.feature.player.PlayerLifeCycle;
import com.hmc.zenkai.feature.forms.FormIds;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record TransformHoldPacket(Action action, boolean active) implements CustomPacketPayload {

    public enum Action {
        TRANSFORM_HOLD, // B sostenido
        DETRANSFORM     // B toque
    }

    public static final Type<TransformHoldPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "transform_hold"));

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

            var stats = sp.getData(ZenkaiDataAttachments.PLAYER_STATS.get());
            var form  = sp.getData(ZenkaiDataAttachments.PLAYER_FORM.get());

            if (!stats.isRaceChosen()) {
                form.resetAll();
                PlayerLifeCycle.syncFormToTrackersAndSelf(sp);
                return;
            }

            if (pkt.action() == Action.DETRANSFORM) {
                // Se pela por CAPAS, y en este orden: el kaioken va ENCIMA de la forma, así
                // que el primer toque lo quita y el segundo devuelve a base. Antes solo se
                // miraba el formId; como el kaioken no es una forma sino otra capa, estando
                // en base la condición era falsa y el toque no hacía absolutamente nada.
                if (!form.dropKaioken() && !FormIds.BASE.equals(form.getFormId())) {
                    form.forceBase();
                }
                PlayerLifeCycle.syncFormToTrackersAndSelf(sp);
                return;
            }

            // Hold de transformación. Las cancelaciones (carga de ki, turbo, carga de técnica
            // en curso) las hace ActionResolver por Matriz A y B: aquí no se repiten.
            // setTransformHeld lo escribe el resolver, no este handler.
            com.hmc.zenkai.feature.action.ActionResolver.setTransformHeld(sp, pkt.active());
            PlayerLifeCycle.syncFormToTrackersAndSelf(sp);
        });
    }
}
