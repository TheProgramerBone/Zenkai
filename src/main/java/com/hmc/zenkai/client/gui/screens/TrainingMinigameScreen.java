package com.hmc.zenkai.client.gui.screens;

/**
 * Implementada por MeditationScreen/TargetPracticeScreen: recibe el TP real que el servidor
 * concedió tras procesar el fin de la sesión (TrainingSessionRewardPacket), vía
 * ClientPayloadHandlers.onTrainingReward — mismo idioma que
 * ClientPayloadHandlers.updateMasterServices (empujar al Screen ABIERTO en vez de reabrirlo).
 */
public interface TrainingMinigameScreen {
    void onRewardReceived(int tpGranted);
}
