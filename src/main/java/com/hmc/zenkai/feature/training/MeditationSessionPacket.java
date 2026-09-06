package com.hmc.zenkai.feature.training;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.config.ServerConfig;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * C2S: fin de una sesión de Meditation (MeditationScreen), al completarse o al cerrar antes de
 * tiempo. Lleva desempeño CRUDO (notas acertadas, mejor racha, duración) — NUNCA un TP ya
 * calculado, mismo principio anti-trampa que TrainingSwingPacket: el cliente mide, el SERVIDOR
 * decide cuánto vale eso (clamps + tarifa de ServerConfig) antes de llamar a
 * TrainingHooks.grantFromMeditation.
 */
public record MeditationSessionPacket(int notesHit, int maxCombo, int sessionDurationTicks)
        implements CustomPacketPayload {

    public static final Type<MeditationSessionPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "meditation_session"));

    public static final StreamCodec<FriendlyByteBuf, MeditationSessionPacket> STREAM_CODEC =
            StreamCodec.of((buf, pkt) -> {
                        buf.writeVarInt(pkt.notesHit());
                        buf.writeVarInt(pkt.maxCombo());
                        buf.writeVarInt(pkt.sessionDurationTicks());
                    },
                    buf -> new MeditationSessionPacket(buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    /** Rate-limit por jugador — mismo espíritu que TrainingData.lastSwingTime, pero sin tocar
     *  el attachment: esta cooldown es puramente anti-spam de packet, nada que el cliente
     *  necesite leer nunca. */
    private static final Map<UUID, Long> LAST_SESSION_END = new ConcurrentHashMap<>();

    /** Techo generoso de densidad de notas: 1 cada 6 ticks (300ms) — más laxo que el intervalo
     *  real del generador cliente (450ms), así que nunca penaliza una sesión legítima, solo
     *  descarta un reporte imposible de un cliente modificado. */
    private static final double MAX_NOTES_PER_TICK = 1.0 / 6.0;
    private static final int MAX_SESSION_TICKS = 3600; // 3 minutos, techo defensivo

    public static void handle(MeditationSessionPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;

            int granted = 0;
            long now = sp.level().getGameTime();
            Long last = LAST_SESSION_END.get(sp.getUUID());
            if (last == null || now - last >= ServerConfig.meditationMinCooldownTicks()) {
                LAST_SESSION_END.put(sp.getUUID(), now);

                int durationTicks = Math.max(1, Math.min(pkt.sessionDurationTicks(), MAX_SESSION_TICKS));
                int maxPossible = (int) Math.max(1, Math.round(durationTicks * MAX_NOTES_PER_TICK));
                int notesHit = Math.max(0, Math.min(pkt.notesHit(), maxPossible));
                int maxCombo = Math.max(0, Math.min(pkt.maxCombo(), maxPossible));

                double rawTp = Math.min(notesHit, maxCombo) * ServerConfig.meditationTpPerCombo();
                rawTp = Math.min(rawTp, ServerConfig.meditationSessionTpCap());
                if (rawTp > 0) granted = TrainingHooks.grantFromMeditation(sp, rawTp);
            }
            // SIEMPRE se responde, aunque sea 0: la pantalla espera este packet para pasar a
            // resultados y nunca debe quedarse colgada por un cooldown o una sesión sin TP.
            PacketDistributor.sendToPlayer(sp, new TrainingSessionRewardPacket(granted));
        });
    }
}
