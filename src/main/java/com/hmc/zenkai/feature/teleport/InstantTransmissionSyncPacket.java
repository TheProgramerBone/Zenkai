package com.hmc.zenkai.feature.teleport;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.ClientZenkaiPalTick;
import com.hmc.zenkai.client.InstantTransmissionClientState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * S2C, self-only (mandado al propio jugador, nunca a trackers): cooldown restante para el
 * badge del HUD (ClientZenkaiHooks) MÁS los ticks de "quieto" acumulados ahora mismo (0 si no
 * está pulsando TAB), que InstantTransmissionCrosshairOverlay usa para saber si ya se cruzó el
 * umbral de "armado" (MENU_ARM_TICKS) y teñir el ícono de la mira. Los dos viajan
 * en el mismo paquete porque los manda el mismo sitio (InstantTransmissionSystem.tick(), una
 * vez por tick) y comparten el mismo criterio de "solo si cambió" — no hace falta separarlos.
 * El aviso de "menú listo" sigue viajando aparte como action bar normal
 * (Player#displayClientMessage), no por este packet.
 *
 * {@code justTeleported} es la excepción al criterio de "solo si cambió": es un PULSO de un solo
 * tick (true exactamente en el paquete que sigue a un TeleportExecution.execute() exitoso, false
 * en cualquier otro), no un estado que se compare contra el valor anterior — el llamador
 * (InstantTransmissionSystem.syncStateIfChanged) lo decide explícitamente en el sitio de la
 * llamada, nunca derivándolo de un cambio de campo. Existe para que
 * ClientZenkaiPalTick.onInstantTransmissionTeleported sepa CUÁNDO el blink real ya ocurrió y el
 * brazo puede bajar solo, en vez de que el cliente tenga que adivinarlo cortando la animación en
 * el mismo tick en que se soltó TAB (que puede ser 1-2 ticks ANTES de que el servidor ejecute el
 * blink de verdad).
 */
public record InstantTransmissionSyncPacket(int cooldownTicks, int stillTicks, boolean justTeleported)
        implements CustomPacketPayload {

    public static final Type<InstantTransmissionSyncPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "instant_transmission_sync"));

    public static final StreamCodec<FriendlyByteBuf, InstantTransmissionSyncPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, InstantTransmissionSyncPacket::cooldownTicks,
                    ByteBufCodecs.VAR_INT, InstantTransmissionSyncPacket::stillTicks,
                    ByteBufCodecs.BOOL, InstantTransmissionSyncPacket::justTeleported,
                    InstantTransmissionSyncPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(InstantTransmissionSyncPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            InstantTransmissionClientState.setCooldownTicks(pkt.cooldownTicks());
            InstantTransmissionClientState.setStillTicks(pkt.stillTicks());
            if (pkt.justTeleported()) ClientZenkaiPalTick.onInstantTransmissionTeleported();
        });
    }
}
