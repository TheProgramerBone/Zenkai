package com.hmc.zenkai.client.gui.screens;

/**
 * Implementada por ShadowTrainingScreen/MeditationScreen/TargetPracticeScreen: reciben datos que
 * el servidor empuja a la pantalla de Training ABIERTA (mismo idioma que
 * ClientPayloadHandlers.updateMasterServices, nunca reabrir la pantalla).
 *
 * `onRewardReceived` es la respuesta a TrainingSessionRewardPacket (fin de sesión de Meditation/
 * Ki Target Practice) — TP concedido + récord ya actualizado, para RESULTS.
 * `onTrainingInfoReceived` es la respuesta a TrainingInfoRequestPacket (disparada desde init(),
 * ver el javadoc de esa clase) — récord persistido + TP potencial de la sesión, para que INTRO
 * los muestre antes de jugar. `potentialTp` es -1 para Shadow (sin techo de sesión discreto),
 * el implementador debe ocultar esa línea en vez de enseñar "-1".
 * Ambos son default no-op: cada pantalla sobreescribe solo lo que de verdad necesita.
 */
public interface TrainingMinigameScreen {
    default void onRewardReceived(int tpGranted, int record) {}

    default void onTrainingInfoReceived(int record, int potentialTp) {}
}
