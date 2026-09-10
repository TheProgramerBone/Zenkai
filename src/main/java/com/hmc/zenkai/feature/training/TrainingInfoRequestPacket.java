package com.hmc.zenkai.feature.training;

import com.hmc.zenkai.Zenkai;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C2S: "dame mi récord y mi TP potencial de este minijuego" — disparado por
 * ShadowTrainingScreen/MeditationScreen/TargetPracticeScreen al abrir su INTRO, para poder
 * mostrar "TP potencial: hasta X" + "Récord: Y" ANTES de jugar (pedido explícito del usuario).
 * Responde con {@link TrainingInfoPacket}. El servidor SIEMPRE es la fuente de estos números
 * (récord persistido en TrainingData, potencial calculado con TrainingHooks.estimatePotential)
 * — el cliente nunca los adivina, mismo espíritu anti-trampa que el resto de Training.
 *
 * `sessionDurationTicks` es la duración de sesión ACTUALMENTE elegida en el stepper de
 * MeditationScreen (Práctica libre)/TargetPracticeScreen — pedido explícito del usuario para
 * Target Practice ("que a mayor tiempo jugando se pueda conseguir mayor TP, que el número de
 * 'up to' refleje ese cambio"), extendido a Meditation por el mismo motivo (su techo YA escalaba
 * con la duración real en MeditationSessionPacket.handle(), pero "TP potencial" en INTRO seguía
 * mostrando el número plano de 30s sin importar el stepper). Ignorado para SHADOW (sin techo de
 * sesión discreto). Clampado en el handler igual que el resto de Training: esto solo alimenta un
 * NÚMERO INFORMATIVO de solo lectura (nunca concede TP), así que el clamp aquí es generoso, no
 * el mismo anti-trampa estricto de MeditationSessionPacket/TargetPracticeSessionPacket.
 *
 * `difficultyFraction` (añadido tras un reporte real del usuario: "siento que hay una
 * discrepancia" entre "TP potential: up to X" y el TP que de verdad se consigue jugando) fija el
 * SEGUNDO techo de la estimación — cuántas notas/orbes caben de verdad en la duración elegida a
 * esta densidad (ver TrainingHooks.meditationAchievableRawTp/targetPracticeAchievableRawTp).
 * Antes "up to X" solo miraba el techo PLANO de ServerConfig (meditation.session_tp_cap=500 por
 * defecto), que con meditation.tp_per_combo=0.5 no lo alcanza NINGUNA sesión real por perfecta
 * que sea — el número prometía más TP del que existe físicamente. Para TARGET_PRACTICE siempre
 * es la dificultad real elegida; para MEDITATION es -1 cuando hay una canción seleccionada (su
 * chart real no vive en el servidor — ver MeditationChartLoader, solo lee `assets/`, dominio de
 * cliente — así que ese segundo techo se omite y se confía en el techo plano ya escalado por la
 * duración REAL de la canción, aproximación ya existente y razonable para ese caso).
 */
public record TrainingInfoRequestPacket(int minigame, int sessionDurationTicks, float difficultyFraction)
        implements CustomPacketPayload {

    public static final int MEDITATION = 0;
    public static final int TARGET_PRACTICE = 1;
    public static final int SHADOW = 2;

    /** Techo generoso para el clamp de sessionDurationTicks (20 min) — muy por encima de
     *  cualquier stepper real de los dos minijuegos, solo para que un cliente modificado no
     *  pueda inflar el número mostrado a algo absurdo. */
    private static final int MAX_DURATION_TICKS = 24000;

    public static final Type<TrainingInfoRequestPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "training_info_request"));

    public static final StreamCodec<FriendlyByteBuf, TrainingInfoRequestPacket> STREAM_CODEC =
            StreamCodec.of((buf, pkt) -> {
                        buf.writeVarInt(pkt.minigame());
                        buf.writeVarInt(pkt.sessionDurationTicks());
                        buf.writeFloat(pkt.difficultyFraction());
                    },
                    buf -> new TrainingInfoRequestPacket(buf.readVarInt(), buf.readVarInt(), buf.readFloat()));

    /** Conveniencia para SHADOW, que no usa ninguno de los dos campos nuevos. */
    public TrainingInfoRequestPacket(int minigame) {
        this(minigame, 0, -1f);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(TrainingInfoRequestPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            TrainingData td = sp.getData(com.hmc.zenkai.registry.ZenkaiDataAttachments.TRAINING.get());
            int durationTicks = Math.max(1, Math.min(pkt.sessionDurationTicks(), MAX_DURATION_TICKS));

            int record;
            int potential;
            switch (pkt.minigame()) {
                case MEDITATION -> {
                    record = td.getBestMeditationTp();
                    double cap = com.hmc.zenkai.config.ServerConfig.meditationSessionTpCap()
                            * (durationTicks / TrainingHooks.SESSION_CAP_BASELINE_TICKS);
                    // difficultyFraction <= 0 = modo Canción (ver javadoc de clase): sin chart en
                    // servidor, no se puede calcular el segundo techo, se confía en el plano.
                    if (pkt.difficultyFraction() > 0f) {
                        cap = Math.min(cap, TrainingHooks.meditationAchievableRawTp(
                                pkt.difficultyFraction(), durationTicks));
                    }
                    potential = TrainingHooks.estimatePotential(sp, cap, TrainingCategory.MEDITATION);
                }
                case TARGET_PRACTICE -> {
                    record = td.getBestTargetPracticeTp();
                    double cap = com.hmc.zenkai.config.ServerConfig.targetPracticeSessionTpCap()
                            * (durationTicks / TrainingHooks.SESSION_CAP_BASELINE_TICKS);
                    cap = Math.min(cap, TrainingHooks.targetPracticeAchievableRawTp(
                            pkt.difficultyFraction(), durationTicks));
                    potential = TrainingHooks.estimatePotential(sp, cap, TrainingCategory.TARGET_PRACTICE);
                }
                default -> {
                    // SHADOW: no hay techo de sesión discreto (el TP sale de golpes/kills
                    // continuos, no de un packet de fin de sesión) — sentinel -1 = "sin potencial
                    // fijo que mostrar", ver el javadoc de TrainingInfoPacket.
                    record = td.getBestShadowTp();
                    potential = -1;
                }
            }
            PacketDistributor.sendToPlayer(sp, new TrainingInfoPacket(pkt.minigame(), record, potential));
        });
    }
}
