package com.hmc.zenkai.feature.combat;

import com.hmc.zenkai.Zenkai;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * S2C, solo al propio jugador: el parry FALLÓ (la ventana se cerró sin parar nada) o se intentó
 * durante el cooldown. Lleva los ticks que faltan para poder volver a abrir la ventana; el cliente
 * pinta el aviso con esa cuenta atrás (ClientZenkaiHooks, insignia del parry fallido).
 */
public record ParryCooldownPacket(int ticksRemaining) implements CustomPacketPayload {

    public static final Type<ParryCooldownPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "parry_cooldown"));

    public static final StreamCodec<FriendlyByteBuf, ParryCooldownPacket> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, ParryCooldownPacket::ticksRemaining,
                    ParryCooldownPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(ParryCooldownPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() ->
                com.hmc.zenkai.client.ParryClientState.onCooldown(pkt.ticksRemaining()));
    }
}
