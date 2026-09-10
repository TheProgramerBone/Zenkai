package com.hmc.zenkai.client;

import com.hmc.zenkai.client.gui.screens.InstantTransmissionMenuScreen;
import com.hmc.zenkai.client.gui.screens.MasterScreen;
import com.hmc.zenkai.client.gui.screens.NpcMarkerScreen;
import com.hmc.zenkai.client.gui.screens.ShadowResultScreen;
import com.hmc.zenkai.client.gui.screens.ShenlongWishScreen;
import com.hmc.zenkai.client.gui.screens.TrainingHubScreen;
import com.hmc.zenkai.client.gui.screens.TrainingMinigameScreen;
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

    /** El reward real de una sesión de Meditation/Ki Target Practice, tras
     *  TrainingSessionRewardPacket. Igual que updateMasterServices: empuja el dato a la
     *  pantalla YA abierta, no hace nada si el jugador ya cerró la screen entre medias. */
    public static void onTrainingReward(int tpGranted, int record) {
        Screen current = Minecraft.getInstance().screen;
        if (current instanceof TrainingMinigameScreen tms) tms.onRewardReceived(tpGranted, record);
    }

    /** Récord + TP potencial de un minijuego de Training, tras TrainingInfoRequestPacket (ver su
     *  javadoc) — igual que onTrainingReward, empuja a la pantalla YA abierta. `minigame` no se
     *  reenvía: cada pantalla ya sabe qué minijuego es ella misma, solo le hacía falta el dato. */
    public static void onTrainingInfo(int minigame, int record, int potentialTp) {
        Screen current = Minecraft.getInstance().screen;
        if (current instanceof TrainingMinigameScreen tms) tms.onTrainingInfoReceived(record, potentialTp);
    }

    /** Resumen de una pelea de "Train with your shadow" (ver ShadowSessionResultPacket) — a
     *  diferencia de onTrainingReward/onTrainingInfo, esto SÍ abre una pantalla nueva en vez de
     *  empujar a una ya abierta: Shadow no tiene GUI propia durante el combate (pasa en el mundo
     *  con el HUD normal), así que este popup es la única forma de enseñar el resultado. */
    public static void onShadowSessionResult(int earnedTp, int record) {
        Minecraft.getInstance().setScreen(new ShadowResultScreen(earnedTp, record));
    }

    /** Eficiencia de entrenamiento actual, UNA por categoría (ver TrainingFatigueRequestPacket/
     *  TrainingCategory) — solo TrainingHubScreen la pide, empujada a ella igual que
     *  onTrainingReward/onTrainingInfo. */
    public static void onTrainingFatigue(double combatEfficiency, double meditationEfficiency,
                                          double targetPracticeEfficiency) {
        Screen current = Minecraft.getInstance().screen;
        if (current instanceof TrainingHubScreen ths) {
            ths.onFatigueReceived(combatEfficiency, meditationEfficiency, targetPracticeEfficiency);
        }
    }

    public static void openInstantTransmissionMenu() {
        // El hold de TAB terminó en menú, no en blink — resuelve la espera de animación con un
        // corte en seco (no hay "teletransportación" que bajar el brazo suavemente aquí).
        ClientZenkaiPalTick.onInstantTransmissionMenuOpened();
        Minecraft.getInstance().setScreen(new InstantTransmissionMenuScreen());
    }
}