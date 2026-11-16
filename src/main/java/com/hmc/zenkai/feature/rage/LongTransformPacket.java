package com.hmc.zenkai.feature.rage;

import com.hmc.zenkai.Zenkai;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/**
 * S2C: este jugador va a transformarse en {@code form} POR PRIMERA VEZ, así que su aura debe reproducir la secuencia LARGA y no la
 * corta. Lo manda {@link FirstTransformations} al propio jugador y a quienes lo ven, ANTES de que el cambio de forma llegue por su
 * sync normal: el cliente apunta el aviso ({@link LongTransformState}) y el compositor de auras lo consume cuando detecta el cambio.
 * Es solo presentación: no concede ni cambia nada, y que el cliente la salte o la reduzca (preferencia) no afecta al servidor.
 */
public record LongTransformPacket(UUID player, ResourceLocation form, String trigger, boolean first) implements CustomPacketPayload {

    public static final Type<LongTransformPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "long_transform"));

    /** {@code first}: es la PRIMERA vez que ese jugador se transforma en la forma (solo entonces sale el título con su nombre; las repeticiones de «siempre larga» no). */
    /** {@code trigger}: nombre del {@link RageTrigger} que despertó la forma, o vacío si la vía no es Rage (elige la pose y la nota de la escena). */
    public static final StreamCodec<FriendlyByteBuf, LongTransformPacket> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> { buf.writeUUID(p.player()); buf.writeResourceLocation(p.form()); buf.writeUtf(p.trigger(), 48); buf.writeBoolean(p.first()); },
            buf -> new LongTransformPacket(buf.readUUID(), buf.readResourceLocation(), buf.readUtf(48), buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(LongTransformPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            LongTransformState.mark(pkt.player(), pkt.form(), pkt.trigger(), pkt.first());
            // La forma se verá cambiar en el estallido de la escena, no antes (presentación del cliente).
            com.hmc.zenkai.client.rage.ClientFormDelay.arm(pkt.player());
        });
    }
}
