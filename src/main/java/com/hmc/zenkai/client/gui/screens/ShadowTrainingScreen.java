package com.hmc.zenkai.client.gui.screens;

import com.hmc.zenkai.client.gui.PanelText;
import com.hmc.zenkai.client.gui.ScreenTitle;
import com.hmc.zenkai.client.gui.ZenkaiPalette;
import com.hmc.zenkai.client.gui.buttons.MinusIconButton;
import com.hmc.zenkai.client.gui.buttons.PanelButton;
import com.hmc.zenkai.client.gui.buttons.PlusIconButton;
import com.hmc.zenkai.feature.training.StartShadowTrainingPacket;
import com.hmc.zenkai.feature.training.TrainingInfoRequestPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Selector de dificultad de "Train with your shadow" (TrainingHubScreen -> aquí). Sin slider
 * propio en el mod (no existe ese widget) — mismo idioma de stepper +/- que ya usa StatsScreen
 * para los atributos, sobre una lista discreta de porcentajes en vez de un rango continuo.
 * "Start" manda StartShadowTrainingPacket y cierra la pantalla: la pelea pasa en el mundo, con
 * el HUD normal del juego, no en una GUI propia (ver el pendiente de UX de la sombra del plan).
 *
 * El bloque de descripción y el stepper solían solaparse: stepperY era un offset FIJO bajo la
 * descripción, pero el texto envuelve a un número de líneas que depende del ancho real de fuente
 * (varía por idioma) — con más líneas de las que el offset fijo esperaba, el stepper se dibujaba
 * ENCIMA de las últimas líneas. Ahora se mide this.font.split(...) UNA VEZ en initContent() (el
 * texto es fijo, no cambia por frame) y todo lo de abajo (stepper, Start/Back) se posiciona
 * relativo a esa altura MEDIDA, nunca a un número fijo adivinado.
 */
public class ShadowTrainingScreen extends ZenkaiMenuScreen implements TrainingMinigameScreen {

    private static final int[] STEPS_PCT = {5, 10, 25, 50, 75, 100, 125, 150, 175, 200};
    private static final int IN_X1 = 10;
    private static final int IN_X2 = 245;

    private int stepIndex = 5; // arranca en 100%

    private List<FormattedCharSequence> descLines;
    private int descY;
    private int stepperY;
    private int startY;

    /** -1 = todavía esperando TrainingInfoPacket (ver onTrainingInfoReceived) — Shadow no tiene
     *  "TP potencial" fijo (sin techo de sesión discreto), solo récord. */
    private int record = -1;

    public ShadowTrainingScreen() {
        super(Component.translatable("screen.zenkai.training_hub.row.shadow"));
    }

    @Override
    protected ZenkaiTab currentTab() { return ZenkaiTab.TRAINING; }

    @Override
    protected void initContent() {
        Component desc = Component.translatable("screen.zenkai.training_hub.row.shadow.tooltip");
        descLines = this.font.split(desc, BG_W - 24);
        descY = CONTENT_TOP + 20;
        stepperY = descY + descLines.size() * 10 + 14;
        startY = BG_H - 12 - PanelButton.H;

        int cx = panelLeft + BG_W / 2;
        addRenderableWidget(new MinusIconButton(cx - 60, panelTop + stepperY, this::decrease));
        addRenderableWidget(new PlusIconButton(cx + 48, panelTop + stepperY, this::increase));

        addRenderableWidget(PanelButton.secondary(
                panelLeft + IN_X1, panelTop + startY,
                Component.translatable("screen.zenkai.back"),
                () -> mc.setScreen(new TrainingHubScreen())));
        addRenderableWidget(PanelButton.primary(
                panelLeft + IN_X2 - PanelButton.W, panelTop + startY,
                Component.translatable("screen.zenkai.training_hub.shadow.start"),
                this::onStart));

        record = -1;
        PacketDistributor.sendToServer(new TrainingInfoRequestPacket(TrainingInfoRequestPacket.SHADOW));
    }

    @Override
    public void onTrainingInfoReceived(int record, int potentialTp) {
        this.record = record;
    }

    private void decrease() { stepIndex = Math.max(0, stepIndex - 1); }
    private void increase() { stepIndex = Math.min(STEPS_PCT.length - 1, stepIndex + 1); }

    private void onStart() {
        float frac = STEPS_PCT[stepIndex] / 100.0f;
        PacketDistributor.sendToServer(new StartShadowTrainingPacket(frac));
        mc.setScreen(null);
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        ScreenTitle.drawAbovePanel(g, this.font, this.title, panelLeft + BG_W / 2, panelTop);
        if (att == null) return;

        int cx = panelLeft + BG_W / 2;

        int ty = panelTop + descY;
        for (var line : descLines) {
            PanelText.onPanel(g, this.font, line, cx - this.font.width(line) / 2, ty, ZenkaiPalette.MUTED_ON_PANEL);
            ty += 10;
        }

        PanelText.centeredOnPanel(g, this.font,
                Component.translatable("screen.zenkai.training_hub.shadow.difficulty",
                        STEPS_PCT[stepIndex]),
                cx, panelTop + stepperY + 2, ZenkaiPalette.LABEL_ON_PANEL);

        if (record >= 0) {
            PanelText.centeredOnPanel(g, this.font,
                    Component.translatable("screen.zenkai.training_hub.record", record),
                    cx, panelTop + stepperY + 14, ZenkaiPalette.MUTED_ON_PANEL);
        }
    }
}
