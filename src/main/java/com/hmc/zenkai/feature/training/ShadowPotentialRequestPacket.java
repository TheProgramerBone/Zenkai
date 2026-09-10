package com.hmc.zenkai.feature.training;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.config.ServerConfig;
import com.hmc.zenkai.feature.forms.FormIds;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C2S: "si matara a la sombra AHORA MISMO con esta dificultad y esta forma simulada, ¿cuánto TP
 * me daría?" — disparado por ShadowTrainingScreen cada vez que cambia el stepper de dificultad O
 * el selector "Opponent form" (pedido explícito del usuario: "quiero que se vea cuánto TP puede
 * conseguir el jugador si el enemigo está en X transformación a X dificultad", tras el primer
 * intento que solo enseñaba un multiplicador sin número absoluto).
 *
 * Responde con el {@link TrainingInfoPacket} YA existente (mismo minigame=SHADOW, mismo
 * `ClientPayloadHandlers.onTrainingInfo`/`TrainingMinigameScreen.onTrainingInfoReceived` que usan
 * los otros dos minijuegos) en vez de inventar un packet de respuesta propio — la forma del dato
 * (record + un número de TP) es idéntica, solo cambia CÓMO se calcula ese número en el servidor.
 * A diferencia de {@link TrainingInfoRequestPacket} (que para SHADOW siempre responde -1: no hay
 * techo de sesión fijo), este SÍ calcula un número real, porque aquí no se pregunta "el techo de
 * una sesión" sino "el reward exacto de UN kill concreto con estos parámetros" — dato bien
 * definido incluso sin sesión discreta.
 *
 * El "reward auto" de un kill (round(victimPl * tpPerPl())) se recalcula aquí en vez de leerlo de
 * {@code EntityStats} porque la sombra ni siquiera existe todavía en este punto (el jugador solo
 * está mirando el selector) — es la MISMA fórmula que {@code EntityStats.resolveReward()} usa para
 * el caso "auto" (el que {@link ShadowTrainingManager#start} le pasa siempre a la sombra), pero esa
 * clase vive en combat.entity y no debe depender de Training, así que se duplica aquí a propósito
 * (si esa fórmula cambia, hay que revisar los dos sitios a mano).
 */
public record ShadowPotentialRequestPacket(float difficultyFraction, ResourceLocation simulatedFormId)
        implements CustomPacketPayload {

    public static final Type<ShadowPotentialRequestPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "shadow_potential_request"));

    public static final StreamCodec<FriendlyByteBuf, ShadowPotentialRequestPacket> STREAM_CODEC =
            StreamCodec.of((buf, pkt) -> {
                        buf.writeFloat(pkt.difficultyFraction());
                        buf.writeResourceLocation(pkt.simulatedFormId());
                    },
                    buf -> new ShadowPotentialRequestPacket(buf.readFloat(), buf.readResourceLocation()));

    /** Conveniencia: sin forma simulada (Base). */
    public ShadowPotentialRequestPacket(float difficultyFraction) {
        this(difficultyFraction, FormIds.BASE);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(ShadowPotentialRequestPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
            if (!att.isRaceChosen()) return;

            float frac = Math.max(ShadowTrainingManager.MIN_FRACTION,
                    Math.min(ShadowTrainingManager.MAX_FRACTION, pkt.difficultyFraction()));
            long victimPl = Math.max(1,
                    Math.round(ShadowTrainingManager.simulatedPowerLevel(sp, att, pkt.simulatedFormId()) * frac));
            double rawTp = Math.max(1, Math.round(victimPl * ServerConfig.tpPerPl()));
            int potential = TrainingHooks.estimateCombatKillReward(sp, rawTp, victimPl);

            TrainingData td = sp.getData(com.hmc.zenkai.registry.ZenkaiDataAttachments.TRAINING.get());
            PacketDistributor.sendToPlayer(sp,
                    new TrainingInfoPacket(TrainingInfoRequestPacket.SHADOW, td.getBestShadowTp(), potential));
        });
    }
}
