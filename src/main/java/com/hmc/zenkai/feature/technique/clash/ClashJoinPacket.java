package com.hmc.zenkai.feature.technique.clash;

import com.hmc.zenkai.Zenkai;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * C2S: «quiero ayudar a este luchador». Lo manda el clic derecho («Usar objeto») apuntando a un
 * participante de un choque de haces (ClashJoinClient). Solo lleva A QUIÉN se apunta y la posición
 * seleccionada del overlay de técnicas; TODO lo demás lo decide el servidor (ClashManager.join):
 * si hay choque, distancia, party, sitio en el equipo, qué técnica hay en esa posición, su daño
 * y su coste de ki. Un cliente modificado no puede elegir ni el equipo ni el daño que aporta.
 */
public record ClashJoinPacket(int targetEntityId, int position) implements CustomPacketPayload {

    public static final Type<ClashJoinPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "clash_join"));

    public static final StreamCodec<FriendlyByteBuf, ClashJoinPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeVarInt(p.targetEntityId());
                        buf.writeVarInt(p.position());
                    },
                    buf -> new ClashJoinPacket(buf.readVarInt(), buf.readVarInt()));

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(ClashJoinPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            ClashManager.join(sp, pkt.targetEntityId(), pkt.position());
        });
    }
}
