package com.hmc.zenkai.client.gui.screens;

import com.hmc.zenkai.client.gui.PanelText;
import com.hmc.zenkai.client.gui.ScreenTitle;
import com.hmc.zenkai.client.gui.ZenkaiPalette;
import com.hmc.zenkai.client.gui.buttons.BackIconButton;
import com.hmc.zenkai.client.gui.buttons.MinusIconButton;
import com.hmc.zenkai.client.gui.buttons.PanelButton;
import com.hmc.zenkai.client.gui.buttons.PlayIconButton;
import com.hmc.zenkai.client.gui.buttons.PlusIconButton;
import com.hmc.zenkai.feature.forms.FormIds;
import com.hmc.zenkai.feature.player.PlayerFormAttachment;
import com.hmc.zenkai.feature.skills.SuperForms;
import com.hmc.zenkai.feature.training.ShadowPotentialRequestPacket;
import com.hmc.zenkai.feature.training.StartShadowTrainingPacket;
import com.hmc.zenkai.registry.ZenkaiDataAttachments;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Selector de dificultad + "Opponent form" de "Train with your shadow" (TrainingHubScreen ->
 * aquí). Sin slider propio en el mod (no existe ese widget) — mismo idioma de stepper +/- que ya
 * usa StatsScreen para los atributos, sobre listas discretas en vez de un rango continuo.
 * "Start" manda StartShadowTrainingPacket y cierra la pantalla: la pelea pasa en el mundo, con
 * el HUD normal del juego, no en una GUI propia (ver el pendiente de UX de la sombra del plan).
 *
 * El bloque de descripción y el stepper solían solaparse: stepperY era un offset FIJO bajo la
 * descripción, pero el texto envuelve a un número de líneas que depende del ancho real de fuente
 * (varía por idioma) — con más líneas de las que el offset fijo esperaba, el stepper se dibujaba
 * ENCIMA de las últimas líneas. Ahora se mide this.font.split(...) UNA VEZ en initContent() (el
 * texto es fijo, no cambia por frame) y todo lo de abajo (steppers, TP potencial, récord,
 * Start/Back) se posiciona relativo a esa altura MEDIDA, nunca a un número fijo adivinado.
 *
 * "Opponent form: SS Blue" solapaba los botones +/- del selector (bug real, captura del usuario):
 * a diferencia de "Difficulty: 100%" (corto, cabía sobrado en el hueco entre botones), el nombre
 * de forma más largo ("Pure Evil Majin") no cabía combinado con el prefijo "Opponent form: " en
 * el mismo renglón centrado. Arreglado separando el prefijo (su propia línea, sin botones, puede
 * ser tan ancho como haga falta) del VALOR (una línea propia, solo el nombre de la forma, entre
 * los botones — igual de corto que "Difficulty: 100%" en el peor caso real del datapack actual).
 */
public class ShadowTrainingScreen extends ZenkaiMenuScreen implements TrainingMinigameScreen {

    private static final int[] STEPS_PCT = {5, 10, 25, 50, 75, 100, 125, 150, 175, 200};
    // El beige real de common_screen.png va de x=12 a x=244 (muestreado píxel a píxel, ver
    // TrainingHubScreen) — 10/245 se metían 2px dentro del marco por cada lado.
    private static final int IN_X1 = 12;
    private static final int IN_X2 = 243;

    private int stepIndex = 5; // arranca en 100%

    private List<FormattedCharSequence> descLines;
    private int descY;
    private int stepperY;
    /** Y de la etiqueta estática "Opponent form" (sin botones, puede ser ancha). */
    private int formLabelY;
    /** Y de los botones +/- del selector de forma + el NOMBRE solo (sin prefijo) entre ellos. */
    private int formValueY;
    private int potentialY;
    private int recordY;
    private int startY;

    /** null hasta que responde ShadowPotentialRequestPacket — récord Y "TP potencial: hasta X"
     *  de matar a la sombra AHORA MISMO con la dificultad/forma elegidas (pedido explícito del
     *  usuario: antes solo se enseñaba un multiplicador sin número absoluto, y cambiar de forma
     *  no se reflejaba en nada visible). Se repite la petición cada vez que cambia CUALQUIERA de
     *  los dos steppers — ver requestPotential(). */
    private Integer record;
    private Integer potential;

    /** Formas de la raza del jugador que ya tiene desbloqueadas (BASE primero) — ver
     *  SuperForms.unlockedChain(). Selector "Opponent form": simula qué PL tendría la sombra si
     *  el jugador llevara puesta esa forma, SIN transformarlo de verdad (pedido explícito del
     *  usuario, ver el javadoc de ShadowTrainingManager.start()). */
    private List<ResourceLocation> formOptions;
    private int formIndex;

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
        formLabelY = stepperY + 16;
        formValueY = formLabelY + 12;
        potentialY = formValueY + 16;
        recordY = potentialY + 12;
        startY = BG_H - 12 - PanelButton.H;

        int cx = panelLeft + BG_W / 2;
        addRenderableWidget(new MinusIconButton(cx - 60, panelTop + stepperY, this::decrease));
        addRenderableWidget(new PlusIconButton(cx + 48, panelTop + stepperY, this::increase));

        // mc.player debería estar siempre presente al abrir esta pantalla (viene de un clic en
        // TrainingHubScreen, que ya requiere att != null) — el fallback a solo BASE es defensivo,
        // mismo espíritu que el resto de la clase (ver el guard de att == null en render()).
        formOptions = mc.player != null ? SuperForms.unlockedChain(mc.player) : List.of(FormIds.BASE);
        formIndex = 0;
        if (mc.player != null) {
            PlayerFormAttachment formAtt = mc.player.getData(ZenkaiDataAttachments.PLAYER_FORM.get());
            formIndex = Math.max(0, formOptions.indexOf(formAtt.getFormId()));
        }
        addRenderableWidget(new MinusIconButton(cx - 60, panelTop + formValueY, this::decreaseForm));
        addRenderableWidget(new PlusIconButton(cx + 48, panelTop + formValueY, this::increaseForm));

        // Back en icono atlas / Start en ▶ — mismo lenguaje que Meditation/Target Practice, con
        // tooltip (pedido explícito del usuario: "que se entienda que son start y back" sin
        // tener que adivinarlo por el icono solo).
        BackIconButton backBtn = new BackIconButton(
                panelLeft + IN_X1, panelTop + startY + (PanelButton.H - 20) / 2, 20,
                () -> mc.setScreen(new TrainingHubScreen()));
        backBtn.setTooltip(Tooltip.create(Component.translatable("screen.zenkai.back")));
        addRenderableWidget(backBtn);

        PlayIconButton startBtn = new PlayIconButton(
                panelLeft + IN_X2 - 24, panelTop + startY + (PanelButton.H - 24) / 2, 20,
                this::onStart);
        startBtn.setTooltip(Tooltip.create(Component.translatable("screen.zenkai.training_hub.shadow.start")));
        addRenderableWidget(startBtn);

        record = null;
        requestPotential();
    }

    /** Interfaz de TrainingMinigameScreen, pero aquí SIEMPRE llega vía ShadowPotentialRequestPacket
     *  (no TrainingInfoRequestPacket — Shadow ya no lo usa, ver requestPotential()), así que
     *  `potentialTp` nunca es el sentinel -1 de "sin techo de sesión". */
    @Override
    public void onTrainingInfoReceived(int record, int potentialTp) {
        this.record = record;
        this.potential = potentialTp;
    }

    /** (Re)pide récord + "TP potencial: hasta X" de un kill AHORA MISMO — disparado al entrar Y
     *  cada vez que cambia la dificultad O la forma simulada (los dos afectan el PL de la
     *  sombra, ver ShadowPotentialRequestPacket). */
    private void requestPotential() {
        potential = null;
        float frac = STEPS_PCT[stepIndex] / 100.0f;
        PacketDistributor.sendToServer(new ShadowPotentialRequestPacket(frac, formOptions.get(formIndex)));
    }

    private void decrease() { stepIndex = Math.max(0, stepIndex - 1); requestPotential(); }
    private void increase() { stepIndex = Math.min(STEPS_PCT.length - 1, stepIndex + 1); requestPotential(); }

    private void decreaseForm() { formIndex = Math.max(0, formIndex - 1); requestPotential(); }
    private void increaseForm() { formIndex = Math.min(formOptions.size() - 1, formIndex + 1); requestPotential(); }

    private void onStart() {
        float frac = STEPS_PCT[stepIndex] / 100.0f;
        PacketDistributor.sendToServer(new StartShadowTrainingPacket(frac, formOptions.get(formIndex)));
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

        // "Opponent form": etiqueta SIN valor en su propia línea (puede ser tan ancha como haga
        // falta, sin botones que limiten el hueco) + el NOMBRE SOLO (sin prefijo) en la línea de
        // los botones — ver el javadoc de clase para el bug que esto arregla.
        PanelText.centeredOnPanel(g, this.font,
                Component.translatable("screen.zenkai.training_hub.shadow.form"),
                cx, panelTop + formLabelY, ZenkaiPalette.MUTED_ON_PANEL);
        ResourceLocation selectedFormId = formOptions.get(formIndex);
        PanelText.centeredOnPanel(g, this.font,
                Component.translatable("form.zenkai." + selectedFormId.getPath()),
                cx, panelTop + formValueY + 2, ZenkaiPalette.LABEL_ON_PANEL);

        if (potential != null) {
            PanelText.centeredOnPanel(g, this.font,
                    Component.translatable("screen.zenkai.training_hub.potential", potential),
                    cx, panelTop + potentialY, ZenkaiPalette.VALUE_ON_PANEL);
        }
        if (record != null) {
            PanelText.centeredOnPanel(g, this.font,
                    Component.translatable("screen.zenkai.training_hub.record", record),
                    cx, panelTop + recordY, ZenkaiPalette.MUTED_ON_PANEL);
        }
    }
}
