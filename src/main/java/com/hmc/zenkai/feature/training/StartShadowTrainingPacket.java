package com.hmc.zenkai.feature.training;

import com.hmc.zenkai.Zenkai;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C2S: "empieza una sesión de Train with your shadow" (ShadowTrainingScreen, botón Start).
 * `difficultyFraction` es el 5%-200% elegido por el jugador — el servidor SIEMPRE lo clampa
 * (nunca confía en el cliente) antes de usarlo como multiplicador de su propio PL limpio.
 */
public record StartShadowTrainingPacket(float difficultyFraction) implements CustomPacketPayload {

    public static final Type<StartShadowTrainingPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "start_shadow_training"));

    public static final StreamCodec<FriendlyByteBuf, StartShadowTrainingPacket> STREAM_CODEC =
            StreamCodec.of((buf, pkt) -> buf.writeFloat(pkt.difficultyFraction()),
                    buf -> new StartShadowTrainingPacket(buf.readFloat()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(StartShadowTrainingPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            float frac = Math.max(ShadowTrainingManager.MIN_FRACTION,
                    Math.min(ShadowTrainingManager.MAX_FRACTION, pkt.difficultyFraction()));
            ShadowTrainingManager.start(sp, frac);
        });
    }
}
