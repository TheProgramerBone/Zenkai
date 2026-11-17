package com.hmc.zenkai.feature.rage;

import com.hmc.zenkai.Zenkai;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C2S: el jugador cortó la presentación de SU primera transformación abriendo una pantalla (las capas visuales propias se
 * apagan; la animación, el sonido y el estado siguen). Sin datos: el servidor solo anota «interrumpida» en la entrada de la
 * {@link RageChronicle} de esa escena. Es historia, no estado de juego: no concede ni quita nada.
 * <p>
 * Validación (un cliente trucado no puede marcar entradas viejas ni inventar escenas): si la entrada ya existe (despertar
 * automático: se escribe al empezar), se marca solo si es reciente; si aún no existe (carga con la tecla: la entrada se escribe
 * al completarse), se guarda la marca solo cuando el servidor tiene una secuencia larga anunciada para ese jugador, y
 * {@link FirstTransformations} la recoge al escribir la entrada. Soltar la tecla antes del estallido no pasa por aquí: no hubo
 * transformación y no hay entrada que marcar (decidido con el usuario el 2026-11-16).
 */
public record LongTransformInterruptedPacket() implements CustomPacketPayload {

    public static final Type<LongTransformInterruptedPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "long_transform_interrupted"));

    public static final StreamCodec<FriendlyByteBuf, LongTransformInterruptedPacket> STREAM_CODEC =
            StreamCodec.unit(new LongTransformInterruptedPacket());

    /** Margen sobre la duración de la escena: latencia y el título, que vive algo más que la secuencia. */
    public static final int WINDOW_TICKS = FirstTransformations.LONG_SCENE_TOTAL_TICKS + 40;

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(LongTransformInterruptedPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            long now = sp.level().getGameTime();
            if (RageChronicle.get(sp).markRecentInterrupted(now, WINDOW_TICKS)) return;
            FirstTransformations.noteInterrupted(sp);
        });
    }
}
