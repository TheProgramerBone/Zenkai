package com.hmc.zenkai.feature.combat;

import com.hmc.zenkai.Zenkai;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C2S: la preferencia del jugador sobre cómo activar el parry (opción de ClientConfig
 * parry.trigger). Solo importa un bit al servidor: si pulsar la guardia debe abrir la ventana de
 * parry y aplazar la guardia real. La tecla de parry ya la filtra el cliente (si no la quiere, no
 * manda ParryPacket). Es una preferencia, no una concesión: no da nada que el parry no dé a todos.
 */
public record ParryPrefPacket(boolean parryOnBlock) implements CustomPacketPayload {

    public static final Type<ParryPrefPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "parry_pref"));

    public static final StreamCodec<FriendlyByteBuf, ParryPrefPacket> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.BOOL, ParryPrefPacket::parryOnBlock,
                    ParryPrefPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(ParryPrefPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            ParryServer.setParryOnBlock(sp, pkt.parryOnBlock());
        });
    }
}
