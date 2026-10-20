package com.hmc.zenkai.feature.party;

import com.hmc.zenkai.Zenkai;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * C2S: "estos son los compañeros (hasta {@link PartyVitalsServer#MAX_SHOWN}) que quiero ver en mi
 * panel de party". Sin esto el servidor tendría que mandar los signos vitales de toda la party a
 * cada miembro; con una party de 32 serían 32 filas por jugador cada medio segundo. Aquí solo
 * viajan las pocas que el jugador eligió.
 *
 * No se confía en el cliente: la lista se recorta al tope y PartyVitalsServer vuelve a comprobar en
 * cada envío que cada UUID sigue siendo miembro de la party del jugador.
 */
public record PartyHudSelectionPacket(List<UUID> ids) implements CustomPacketPayload {

    public static final Type<PartyHudSelectionPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "party_hud_selection"));

    public static final StreamCodec<FriendlyByteBuf, PartyHudSelectionPacket> STREAM_CODEC =
            StreamCodec.of(PartyHudSelectionPacket::encode, PartyHudSelectionPacket::decode);

    private static void encode(FriendlyByteBuf buf, PartyHudSelectionPacket pkt) {
        int n = Math.min(pkt.ids().size(), PartyVitalsServer.MAX_SHOWN);
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++) buf.writeUUID(pkt.ids().get(i));
    }

    private static PartyHudSelectionPacket decode(FriendlyByteBuf buf) {
        // El tope se aplica al LEER: un cliente tramposo no puede hacer que se reserve una lista enorme.
        int n = Math.min(buf.readVarInt(), PartyVitalsServer.MAX_SHOWN);
        List<UUID> ids = new ArrayList<>(n);
        for (int i = 0; i < n; i++) ids.add(buf.readUUID());
        return new PartyHudSelectionPacket(ids);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(PartyHudSelectionPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp) PartyVitalsServer.setSelection(sp, pkt.ids());
        });
    }
}
