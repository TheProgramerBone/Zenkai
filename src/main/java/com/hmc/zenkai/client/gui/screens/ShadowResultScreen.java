package com.hmc.zenkai.client.gui.screens;

import com.hmc.zenkai.client.gui.CommonPanel;
import com.hmc.zenkai.client.gui.NineSlice;
import com.hmc.zenkai.client.gui.PanelText;
import com.hmc.zenkai.client.gui.ScreenTitle;
import com.hmc.zenkai.client.gui.ZenkaiPalette;
import com.hmc.zenkai.client.gui.buttons.BackIconButton;
import com.hmc.zenkai.client.gui.widgets.TrainingWidgets;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

/**
 * Popup de resumen al morir el clon de "Train with your shadow" (ShadowSessionResultPacket) —
 * pedido explícito del usuario (Pista G del plan de pulido de Training): Shadow es el único de
 * los 3 minijuegos sin GUI propia durante la sesión (la pelea pasa en el mundo con el HUD
 * normal), así que este es el único sitio donde se ve el TP ganado y el récord.
 *
 * `extends Screen` directo, NO ZenkaiMenuScreen: se abre a mitad de partida sobre el mundo, no
 * como parte de la navegación del hub — mismo idioma de panel/fondo compartido que RESULTS de
 * MeditationScreen/TargetPracticeScreen (CommonPanel, blitteado a mano), pero sin la
 * barra de pestañas ni el resto del chrome del menú. isPauseScreen() false a propósito: un golpe
 * de gracia contra la sombra no debería congelar el juego para siempre si el jugador tarda en
 * leer el resultado.
 */
public class ShadowResultScreen extends Screen {

    private final int earnedTp;
    private final int record;

    private int panelLeft, panelTop;
    private int panelW, panelH;

    public ShadowResultScreen(int earnedTp, int record) {
        super(Component.translatable("screen.zenkai.shadow_result.title"));
        this.earnedTp = earnedTp;
        this.record = record;
    }

    @Override
    protected void init() {
        var w = CommonPanel.fitAxis(this.width, CommonPanel.MARGIN, CommonPanel.MARGIN);
        // Misma reserva inferior que ZenkaiMenuScreen (TrainingHub y Shadow): con CommonPanel.MARGIN el panel
        // quedaba 5 px de GUI más abajo y saltaba al entrar desde el hub (medido con ScreenShots, 2026-10-29).
        var h = CommonPanel.fitAxis(this.height, CommonPanel.TITLE_RESERVE, ZenkaiMenuScreen.TAB_RESERVE);
        panelW = w.size();
        panelH = h.size();
        panelLeft = w.pos();
        panelTop = h.pos();

        // Solo «Atrás» (icono, como el resto de la plantilla de Training): la sombra no se reintenta desde aquí.
        BackIconButton back = new BackIconButton(
                CommonPanel.iconLeft(panelLeft), CommonPanel.iconBottom(panelTop, panelH), CommonPanel.ICON,
                () -> this.onClose());
        back.setTooltip(Tooltip.create(Component.translatable("screen.zenkai.close")));
        addRenderableWidget(back);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(null);
    }

    @Override
    public void renderBackground(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        CommonPanel.blit(g, panelLeft, panelTop, panelW, panelH);
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);

        int cx = panelLeft + panelW / 2;

        ScreenTitle.drawAbovePanel(g, this.font, this.title, cx, panelTop);

        TrainingWidgets.result(g, this.font, panelLeft, panelTop, panelW, panelH, earnedTp, record, null);
    }

    @Override public boolean isPauseScreen() { return false; }
}
