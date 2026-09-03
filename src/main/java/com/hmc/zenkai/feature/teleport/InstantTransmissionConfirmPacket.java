package com.hmc.zenkai.feature.teleport;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.event.tick.InstantTransmissionSystem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C2S, sin campos. Clic derecho pulsado mientras TAB sigue sostenido — el gesto de confirmación
 * del blink (estilo Dragon Block C, pedido explícito del usuario: "mantener la tecla + clic
 * derecho" en vez de que soltar TAB solo ya teletransporte por error). Lo manda KeyBindings
 * desde InputEvent.MouseButton.Pre (solo el FLANCO de pulsar, cancelando el evento para que el
 * clic no dispare además una interacción normal — abrir un cofre, comer, etc. — mientras dura
 * este gesto). El servidor vuelve a comprobar que TAB sigue pulsado de verdad
 * (InstantTransmissionAttachment.isHolding) antes de blinkear: un packet suelto/duplicado/tarde
 * sin el hold real detrás no hace nada, igual que cualquier otro camino de este sistema.
 */
public record InstantTransmissionConfirmPacket() implements CustomPacketPayload {

    public static final Type<InstantTransmissionConfirmPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "instant_transmission_confirm"));

    public static final StreamCodec<FriendlyByteBuf, InstantTransmissionConfirmPacket> STREAM_CODEC =
            StreamCodec.unit(new InstantTransmissionConfirmPacket());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(InstantTransmissionConfirmPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            InstantTransmissionSystem.confirmBlink(sp);
        });
    }
}
