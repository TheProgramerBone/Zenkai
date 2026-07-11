package com.hmc.zenkai.core.network.feature.training;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.core.training.TrainingHooks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C2S: "di un golpe al aire con la mano vacía" (entrenamiento). El servidor NO confía en el
 * cliente: revalida mano vacía y aplica rate-limit + coste de stamina en TrainingHooks.
 */
public record TrainingSwingPacket() implements CustomPacketPayload {

    public static final Type<TrainingSwingPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "training_swing"));

    public static final StreamCodec<FriendlyByteBuf, TrainingSwingPacket> STREAM_CODEC =
            StreamCodec.of((buf, pkt) -> {}, buf -> new TrainingSwingPacket());

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(TrainingSwingPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            if (!sp.getItemInHand(InteractionHand.MAIN_HAND).isEmpty()) return; // puños
            TrainingHooks.grantFromSwing(sp);
        });
    }
}