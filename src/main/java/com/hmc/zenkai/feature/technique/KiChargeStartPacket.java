package com.hmc.zenkai.feature.technique;

import com.hmc.zenkai.Zenkai;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C2S: "empiezo/dejo de cargar la técnica del slot N".
 * La carga se acumula en el CLIENTE y no llegaba al servidor hasta soltar (KiFirePacket),
 * así que el servidor no sabía que estabas cargando: ni sonido para los demás, ni bola en
 * las manos, ni aura de carga en jugadores remotos. Esto lo arregla con dos campos.
 * No lleva el progreso: el servidor apunta el tick de inicio y los clientes derivan el
 * crecimiento de la bola solos. Sincronizar un contador cada tick sería tirar ancho de banda.
 */
public record KiChargeStartPacket(int slot, boolean charging) implements CustomPacketPayload {

    public static final Type<KiChargeStartPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "ki_charge_start"));

    public static final StreamCodec<FriendlyByteBuf, KiChargeStartPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, KiChargeStartPacket::slot,
                    ByteBufCodecs.BOOL, KiChargeStartPacket::charging,
                    KiChargeStartPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(KiChargeStartPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            if (pkt.charging()) KiChargeServer.start(sp, pkt.slot());
            else KiChargeServer.stop(sp);
        });
    }
}