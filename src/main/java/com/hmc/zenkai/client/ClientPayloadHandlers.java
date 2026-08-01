package com.hmc.zenkai.client;

import com.hmc.zenkai.client.gui.screens.NpcMarkerScreen;
import com.hmc.zenkai.network.OpenNpcMarkerPayload;
import net.minecraft.client.Minecraft;

/** Handlers de payloads que tocan clases de cliente. NUNCA referenciar desde código común
 *  fuera de un lambda de playToClient: cargaría clases de cliente en un servidor dedicado. */
public final class ClientPayloadHandlers {
    private ClientPayloadHandlers() {}

    public static void openNpcMarker(OpenNpcMarkerPayload p) {
        Minecraft.getInstance().setScreen(new NpcMarkerScreen(
                p.pos(), p.npcType(), p.yaw(), p.offX(), p.offY(), p.offZ()));
    }
}