package com.hmc.zenkai.client;

import com.hmc.zenkai.client.gui.screens.MasterScreen;
import com.hmc.zenkai.client.gui.screens.NpcMarkerScreen;
import com.hmc.zenkai.client.gui.screens.ShenlongWishScreen;
import com.hmc.zenkai.network.MasterServicesUpdatePayload;
import com.hmc.zenkai.network.OpenMasterPayload;
import com.hmc.zenkai.network.OpenNpcMarkerPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** Handlers de payloads que tocan clases de cliente. NUNCA referenciar desde código común
 *  fuera de un lambda de playToClient: cargaría clases de cliente en un servidor dedicado. */
public final class ClientPayloadHandlers {
    private ClientPayloadHandlers() {}

    public static void openNpcMarker(OpenNpcMarkerPayload p) {
        Minecraft.getInstance().setScreen(new NpcMarkerScreen(
                p.pos(), p.npcType(), p.yaw(), p.offX(), p.offY(), p.offZ()));
    }

    public static void openMaster(OpenMasterPayload p) {
        Minecraft.getInstance().setScreen(new MasterScreen(p.masterId(), p.entityId(), p.services()));
    }

    /** Tras un claim exitoso: refresca la lista de servicios de la pantalla YA abierta, sin
     *  reabrirla (ver MasterServicesUpdatePayload). No hace nada si la pantalla actual no es
     *  MasterScreen (se cerró entre medias) — el packet llega igual, solo se ignora. */
    public static void updateMasterServices(MasterServicesUpdatePayload p) {
        Screen current = Minecraft.getInstance().screen;
        if (current instanceof MasterScreen ms) ms.updateServices(p.services());
    }

    public static void openWishScreen() {
        Minecraft.getInstance().setScreen(new ShenlongWishScreen());
    }
}