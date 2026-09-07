package com.hmc.zenkai.client.gui.screens;

import com.hmc.zenkai.client.gui.PanelText;
import com.hmc.zenkai.client.gui.ScreenTitle;
import com.hmc.zenkai.client.gui.ZenkaiPalette;
import com.hmc.zenkai.client.gui.buttons.PanelButton;
import net.minecraft.ChatFormatting;
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
 * MeditationScreen/TargetPracticeScreen (ZenkaiMenuScreen.BG_TEX blitteado a mano), pero sin la
 * barra de pestañas ni el resto del chrome del menú. isPauseScreen() false a propósito: un golpe
 * de gracia contra la sombra no debería congelar el juego para siempre si el jugador tarda en
 * leer el resultado.
 */
public class ShadowResultScreen extends Screen {

    private final int earnedTp;
    private final int record;

    private int panelLeft, panelTop;

    public ShadowResultScreen(int earnedTp, int record) {
        super(Component.translatable("screen.zenkai.shadow_result.title"));
        this.earnedTp = earnedTp;
        this.record = record;
    }

    @Override
    protected void init() {
        panelLeft = (this.width - ZenkaiMenuScreen.BG_W) / 2;
        panelTop = (this.height - ZenkaiMenuScreen.BG_H) / 2;

        int y = panelTop + ZenkaiMenuScreen.BG_H - 12 - PanelButton.H;
        addRenderableWidget(PanelButton.primary(
                panelLeft + (ZenkaiMenuScreen.BG_W - PanelButton.W) / 2, y,
                Component.translatable("screen.zenkai.close"), () -> this.onClose()));
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(null);
    }

    @Override
    public void renderBackground(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        g.blit(ZenkaiMenuScreen.BG_TEX, panelLeft, panelTop, 0, 0,
                ZenkaiMenuScreen.BG_W, ZenkaiMenuScreen.BG_H);
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);

        int cx = panelLeft + ZenkaiMenuScreen.BG_W / 2;
        int ty = panelTop + 50;

        ScreenTitle.drawAbovePanel(g, this.font, this.title, cx, panelTop);

        PanelText.centeredOnPanel(g, this.font,
                Component.translatable("screen.zenkai.shadow_result.earned", earnedTp)
                        .copy().withStyle(ChatFormatting.BOLD),
                cx, ty, ZenkaiPalette.OK_ON_PANEL);
        ty += 16;

        PanelText.centeredOnPanel(g, this.font,
                Component.translatable("screen.zenkai.training_hub.record", record),
                cx, ty, ZenkaiPalette.LABEL_ON_PANEL);
        ty += 16;

        if (earnedTp > 0 && earnedTp == record) {
            PanelText.centeredOnPanel(g, this.font,
                    Component.translatable("screen.zenkai.training_hub.new_record")
                            .copy().withStyle(ChatFormatting.BOLD),
                    cx, ty, ZenkaiPalette.VALUE_ON_PANEL);
        }
    }

    @Override public boolean isPauseScreen() { return false; }
}
