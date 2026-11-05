package com.hmc.zenkai.client.gui.screens.wishes;

import com.hmc.zenkai.client.gui.ZenkaiPalette;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/**
 * Campo de texto de los deseos de Shenlong con la paleta verde: el {@code EditBox} de vanilla pinta
 * un recuadro negro con borde blanco que desentona sobre el panel. Aquí el marco y el fondo los
 * pone esta clase (el rectángulo EXTERIOR es el que se pasa) y el campo vanilla va sin borde y
 * metido 4 px, para que el texto no toque el marco.
 */
final class ShenronEditBox extends EditBox {
    private static final int PAD = 4;
    private final int outerX, outerY, outerW, outerH;

    ShenronEditBox(Font font, int x, int y, int w, int h, Component narration) {
        super(font, x + PAD, y, w - 2 * PAD, h, narration);
        setBordered(false);
        this.outerX = x;
        this.outerY = y;
        this.outerW = w;
        this.outerH = h;
    }

    @Override
    public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int border = isFocused() ? ZenkaiPalette.SHENRON_BORDER_OUT : ZenkaiPalette.SHENRON_BORDER_MID;
        g.fill(outerX, outerY, outerX + outerW, outerY + outerH, border);
        g.fill(outerX + 1, outerY + 1, outerX + outerW - 1, outerY + outerH - 1, ZenkaiPalette.SHENRON_BG);
        // Sin borde, el EditBox pinta el texto pegado a su Y (no lo centra): se baja solo mientras se dibuja.
        setY(outerY + (outerH - 8) / 2);
        super.renderWidget(g, mouseX, mouseY, partialTick);
        setY(outerY);
    }
}
