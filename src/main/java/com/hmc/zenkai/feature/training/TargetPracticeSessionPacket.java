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
 * C2S: fin de una sesión de Ki Target Practice (TargetPracticeScreen) — un bombazo/calavera
 * termina la ronda de golpe. Mismo principio anti-trampa que MeditationSessionPacket: desempeño
 * CRUDO (orbes reventados, bombas tocadas, duración), el servidor calcula/capa el TP.
 *
 * `bombsHit` es 1 si la sesión terminó por tocar una bomba (informativo, RESULTS lo usa para el
 * mensaje "You hit a bomb") pero NO afecta el cálculo de TP — pedido explícito del usuario:
 * tocar una bomba termina la ronda, no borra lo ya ganado. Antes de este fix `handle()` reportaba
 * `orbsPopped=0` cuando `bombsHit=1`, así que perder por bomba literalmente te dejaba sin nada de
 * lo reventado hasta ese punto; ahora `orbsPopped` viaja siempre tal cual, gane o pierda la ronda.
 */
public record TargetPracticeSessionPacket(int orbsPopped, int bombsHit, int sessionDurationTicks)
        implements CustomPacketPayload {

    public static final Type<TargetPracticeSessionPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "target_practice_session"));

    public static final StreamCodec<FriendlyByteBuf, TargetPracticeSessionPacket> STREAM_CODEC =
            StreamCodec.of((buf, pkt) -> {
                        buf.writeVarInt(pkt.orbsPopped());
                        buf.writeVarInt(pkt.bombsHit());
                        buf.writeVarInt(pkt.sessionDurationTicks());
                    },
                    buf -> new TargetPracticeSessionPacket(
                            buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    private static final Map<UUID, Long> LAST_SESSION_END = new ConcurrentHashMap<>();

    /** Techo generoso: 1 orbe cada 8 ticks (400ms) — más laxo que el ritmo real de aparición
     *  del cliente, solo descarta un reporte imposible. */
    private static final double MAX_ORBS_PER_TICK = 1.0 / 8.0;
    public static final int MAX_SESSION_TICKS = 3600;

    public static void handle(TargetPracticeSessionPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            TrainingData td = sp.getData(com.hmc.zenkai.registry.ZenkaiDataAttachments.TRAINING.get());

            int granted = 0;
            long now = sp.level().getGameTime();
            Long last = LAST_SESSION_END.get(sp.getUUID());
            if (last == null || now - last >= ServerConfig.targetPracticeMinCooldownTicks()) {
                LAST_SESSION_END.put(sp.getUUID(), now);

                int durationTicks = Math.max(1, Math.min(pkt.sessionDurationTicks(), MAX_SESSION_TICKS));
                int maxPossible = (int) Math.max(1, Math.round(durationTicks * MAX_ORBS_PER_TICK));
                // Reportado tal cual, gane o pierda la ronda (ver el javadoc de la clase) — antes
                // esto se ponía a 0 si bombsHit=1, así que tocar una bomba borraba lo ganado.
                int orbsPopped = Math.max(0, Math.min(pkt.orbsPopped(), maxPossible));

                double rawTp = orbsPopped * ServerConfig.targetPracticeTpPerOrb();
                // El techo escala con la duración REAL de la sesión (mismo principio que
                // MeditationSessionPacket, ver TrainingHooks.SESSION_CAP_BASELINE_TICKS) — antes
                // era un número plano pensado para los 30s fijos que tenía el minijuego, así que
                // una sesión más larga (ver TargetPracticeScreen.DURATION_STEPS_SEC) no rendía
                // más aunque el jugador jugara más tiempo.
                double sessionCap = ServerConfig.targetPracticeSessionTpCap()
                        * (durationTicks / TrainingHooks.SESSION_CAP_BASELINE_TICKS);
                rawTp = Math.min(rawTp, sessionCap);
                if (rawTp > 0) granted = TrainingHooks.grantFromTargetPractice(sp, rawTp);

                if (granted > td.getBestTargetPracticeTp()) td.setBestTargetPracticeTp(granted);
            }
            PacketDistributor.sendToPlayer(sp, new TrainingSessionRewardPacket(granted, td.getBestTargetPracticeTp()));
        });
    }
}
