package com.hmc.zenkai.feature.dialogue;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/** Punto único para abrir un diálogo con un NPC. */
public final class DialogueService {
    private DialogueService() {}

    /** Distancia máxima (bloques) a la que se acepta una opción: la del clic derecho con holgura. */
    public static final double RANGE = 8.0;

    /** Opción de cierre. Siempre disponible; no llega al NPC. */
    public static OpenDialoguePayload.Choice leave() {
        return new OpenDialoguePayload.Choice("leave", Component.translatable("dialogue.zenkai.choice.leave"));
    }

    public static void open(ServerPlayer sp, Entity npc, List<Component> pages, List<OpenDialoguePayload.Choice> choices) {
        PacketDistributor.sendToPlayer(sp,
                new OpenDialoguePayload(npc.getId(), npc.getDisplayName(), pages, choices));
    }
}
