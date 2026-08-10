package com.hmc.zenkai.feature.ki;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.feature.player.PlayerLifeCycle;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.feature.skills.SkillEffects;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C2S: fijar el % de Ki Control a un valor concreto. Lo manda la barra arrastrable de la
 * pantalla de stats.
 * PowerPercentPacket (el existente) resta cinco puntos por pulsación y es lo correcto para la
 * tecla: en combate no vas a soltar el ratón para buscar un valor exacto, quieres bajar un
 * escalón sin dejar de mirar. Este otro es para el otro momento — el jugador parado, con la
 * ficha abierta, decidiendo a qué porcentaje quiere andar por ahí. Son dos gestos distintos y
 * cada uno merece su paquete; unificarlos en uno con delta obligaría al arrastre a mandar un
 * mensaje por píxel recorrido.
 * NO manda mensaje de chat, a diferencia del de la tecla: ahí el aviso en el action bar es la
 * única realimentación que hay, pero aquí el jugador está mirando la barra moverse.
 * El clamp al techo de la habilidad lo hace el servidor vía setPowerPercent, así que un paquete
 * fabricado a mano con 100 no salta el límite de Ki Control.
 */
public record SetPowerPercentPacket(int percent) implements CustomPacketPayload {

    public static final Type<SetPowerPercentPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "power_percent_set"));

    public static final StreamCodec<FriendlyByteBuf, SetPowerPercentPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, pkt) -> buf.writeVarInt(pkt.percent()),
                    buf -> new SetPowerPercentPacket(buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SetPowerPercentPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
            if (!att.isRaceChosen()) return;
            if (att.setPowerPercent(pkt.percent(), SkillEffects.maxPowerPercent(sp))) {
                PlayerLifeCycle.syncIfServer(sp);
            }
        });
    }
}