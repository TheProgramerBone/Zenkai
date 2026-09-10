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
 * tiempo. Lleva desempeño CRUDO (golpes desglosados por tier de precisión SICK/GOOD/OK, fallos,
 * mejor racha, duración) — NUNCA un TP ni un % de precisión ya calculados, mismo principio
 * anti-trampa que TrainingSwingPacket: el cliente mide, el SERVIDOR decide cuánto vale eso
 * (clamps + tarifa de ServerConfig + el factor de precisión de {@link #handle}) antes de llamar a
 * TrainingHooks.grantFromMeditation. `notesHit` ya no viaja como campo propio — se deriva de
 * `sickCount+goodCount+okCount` (una sola fuente de verdad; MeditationScreen garantiza por
 * construcción que un golpe cae en exactamente un tier).
 */
public record MeditationSessionPacket(
        int sickCount, int goodCount, int okCount, int missCount, int maxCombo, int sessionDurationTicks)
        implements CustomPacketPayload {

    public static final Type<MeditationSessionPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "meditation_session"));

    /** Peso de cada tier de precisión (0.0-1.0) al promediar el % de accuracy estilo FNF — SICK
     *  cuenta como perfecto, MISS como 0 (implícito, sin constante). Públicos a propósito:
     *  MeditationScreen usa EXACTAMENTE estos mismos números para el % que muestra en vivo/en
     *  RESULTS, así que en el caso honesto el número que ve el jugador coincide con el factor que
     *  de verdad aplicó el servidor sobre su TP (ver handle()). */
    public static final double WEIGHT_SICK = 1.0;
    public static final double WEIGHT_GOOD = 0.7;
    public static final double WEIGHT_OK = 0.4;

    public static final StreamCodec<FriendlyByteBuf, MeditationSessionPacket> STREAM_CODEC =
            StreamCodec.of((buf, pkt) -> {
                        buf.writeVarInt(pkt.sickCount());
                        buf.writeVarInt(pkt.goodCount());
                        buf.writeVarInt(pkt.okCount());
                        buf.writeVarInt(pkt.missCount());
                        buf.writeVarInt(pkt.maxCombo());
                        buf.writeVarInt(pkt.sessionDurationTicks());
                    },
                    buf -> new MeditationSessionPacket(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                            buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    /** Rate-limit por jugador — mismo espíritu que TrainingData.lastSwingTime, pero sin tocar
     *  el attachment: esta cooldown es puramente anti-spam de packet, nada que el cliente
     *  necesite leer nunca. */
    private static final Map<UUID, Long> LAST_SESSION_END = new ConcurrentHashMap<>();

    /** Techo generoso de densidad de notas: 1 cada 2 ticks (100ms) — más laxo que el intervalo
     *  real del generador de Práctica libre (450ms) Y que la separación mínima entre dos notas
     *  de un chart de canción (ver gen_meditation_chart.py, MIN_ONSET_GAP_S=150ms; Pigstep llega
     *  a ~3.4 notas/s de media), así que nunca penaliza una sesión legítima de ningún modo, solo
     *  descarta un reporte imposible de un cliente modificado. */
    private static final double MAX_NOTES_PER_TICK = 1.0 / 2.0;
    // 5 minutos: cubre práctica libre (30s) Y el modo Canción (discos vanilla de hasta ~3-4 min,
    // ver el set curado de CuratedSong) con margen para añadir alguno más largo después.
    public static final int MAX_SESSION_TICKS = 6000;

    public static void handle(MeditationSessionPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            TrainingData td = sp.getData(com.hmc.zenkai.registry.ZenkaiDataAttachments.TRAINING.get());

            int granted = 0;
            long now = sp.level().getGameTime();
            Long last = LAST_SESSION_END.get(sp.getUUID());
            if (last == null || now - last >= ServerConfig.meditationMinCooldownTicks()) {
                LAST_SESSION_END.put(sp.getUUID(), now);

                int durationTicks = Math.max(1, Math.min(pkt.sessionDurationTicks(), MAX_SESSION_TICKS));
                int maxPossible = (int) Math.max(1, Math.round(durationTicks * MAX_NOTES_PER_TICK));

                int sick = Math.max(0, pkt.sickCount());
                int good = Math.max(0, pkt.goodCount());
                int ok = Math.max(0, pkt.okCount());
                int miss = Math.max(0, pkt.missCount());
                int totalJudged = sick + good + ok + miss;
                // Mismo espíritu anti-trampa que el clamp de notesHit/maxCombo de antes, extendido
                // a los 4 contadores nuevos: si el total reportado supera lo físicamente posible
                // para la duración de la sesión, se reescalan los 4 proporcionalmente en vez de
                // solo cortar en seco (así un cliente modificado no puede "esconder" golpes falsos
                // detrás de un montón de MISS para inflar el denominador de precisión).
                if (totalJudged > maxPossible && totalJudged > 0) {
                    double scale = maxPossible / (double) totalJudged;
                    sick = (int) (sick * scale);
                    good = (int) (good * scale);
                    ok = (int) (ok * scale);
                    miss = (int) (miss * scale);
                    totalJudged = sick + good + ok + miss;
                }
                int notesHit = sick + good + ok;
                int maxCombo = Math.max(0, Math.min(pkt.maxCombo(), maxPossible));

                // % de precisión estilo FNF (SICK = perfecto), pedido explícito del usuario como
                // factor real de recompensa, no solo cosmético — ver WEIGHT_* y el mismo cálculo
                // en MeditationScreen.accuracyPercent() (el que ve el jugador en vivo).
                double accuracy = totalJudged > 0
                        ? (sick * WEIGHT_SICK + good * WEIGHT_GOOD + ok * WEIGHT_OK) / totalJudged
                        : 1.0;

                double rawTp = Math.min(notesHit, maxCombo) * ServerConfig.meditationTpPerCombo() * accuracy;
                double sessionCap = ServerConfig.meditationSessionTpCap()
                        * (durationTicks / TrainingHooks.SESSION_CAP_BASELINE_TICKS);
                rawTp = Math.min(rawTp, sessionCap);
                if (rawTp > 0) granted = TrainingHooks.grantFromMeditation(sp, rawTp);

                if (granted > td.getBestMeditationTp()) td.setBestMeditationTp(granted);
            }
            // SIEMPRE se responde, aunque sea 0: la pantalla espera este packet para pasar a
            // resultados y nunca debe quedarse colgada por un cooldown o una sesión sin TP.
            PacketDistributor.sendToPlayer(sp, new TrainingSessionRewardPacket(granted, td.getBestMeditationTp()));
        });
    }
}
