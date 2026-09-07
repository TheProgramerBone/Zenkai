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
 */
public record TrainingInfoRequestPacket(int minigame) implements CustomPacketPayload {

    public static final int MEDITATION = 0;
    public static final int TARGET_PRACTICE = 1;
    public static final int SHADOW = 2;

    public static final Type<TrainingInfoRequestPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "training_info_request"));

    public static final StreamCodec<FriendlyByteBuf, TrainingInfoRequestPacket> STREAM_CODEC =
            StreamCodec.of((buf, pkt) -> buf.writeVarInt(pkt.minigame()),
                    buf -> new TrainingInfoRequestPacket(buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(TrainingInfoRequestPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            TrainingData td = sp.getData(com.hmc.zenkai.registry.ZenkaiDataAttachments.TRAINING.get());

            int record;
            int potential;
            switch (pkt.minigame()) {
                case MEDITATION -> {
                    record = td.getBestMeditationTp();
                    potential = TrainingHooks.estimatePotential(sp, com.hmc.zenkai.config.ServerConfig.meditationSessionTpCap());
                }
                case TARGET_PRACTICE -> {
                    record = td.getBestTargetPracticeTp();
                    potential = TrainingHooks.estimatePotential(sp, com.hmc.zenkai.config.ServerConfig.targetPracticeSessionTpCap());
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
