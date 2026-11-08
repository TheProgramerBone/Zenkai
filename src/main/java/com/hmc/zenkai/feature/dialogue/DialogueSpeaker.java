package com.hmc.zenkai.feature.dialogue;

import net.minecraft.server.level.ServerPlayer;

/**
 * Un NPC que habla con una pantalla de diálogo ({@link DialogueService#open}). Recibe el id de la opción que eligió el
 * jugador. El id lo manda el cliente, así que NO es de fiar: cada implementación vuelve a comprobar el estado del jugador
 * (¿sigue muerto?, ¿le acepta el maestro?) en vez de suponer que la opción se ofreció de verdad.
 */
public interface DialogueSpeaker {
    /** Opción pulsada. {@code "leave"} es el cierre estándar y no llega aquí: lo resuelve el servicio. */
    void onDialogueChoice(ServerPlayer player, String choiceId);
}
