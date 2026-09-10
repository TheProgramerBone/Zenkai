package com.hmc.zenkai.feature.spacepod;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.content.entity.misc.SpacePodEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C2S: el jugador eligió un planeta en el menú galáctico (GalacticMenuScreen). El servidor
 * decide TODO de nuevo — que siga realmente montado en una SpacePodEntity, que el destino
 * exista y sea distinto de su dimensión actual — mismo criterio que TeleportRequestPacket: un
 * cliente modificado no puede pedir un despegue sin estar de verdad en una nave.
 * destinationId cabe de sobra en 16 bytes: los ids vienen de SpacePodDestination.id() (nombres
 * de enum en minúsculas, "earth"/"namek"), nunca de texto libre del jugador.
 */
public record SpacePodLaunchPacket(String destinationId) implements CustomPacketPayload {

    public static final Type<SpacePodLaunchPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "space_pod_launch"));

    public static final StreamCodec<FriendlyByteBuf, SpacePodLaunchPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, pkt) -> buf.writeUtf(pkt.destinationId(), 16),
                    buf -> new SpacePodLaunchPacket(buf.readUtf(16)));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SpacePodLaunchPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            if (!(sp.getVehicle() instanceof SpacePodEntity pod)) return;

            SpacePodDestination dest = SpacePodDestination.byId(pkt.destinationId());
            if (dest == null) return;
            if (dest.dimension().equals(sp.serverLevel().dimension())) return; // ya estás ahí

            pod.beginLaunch(sp, dest);
        });
    }
}
