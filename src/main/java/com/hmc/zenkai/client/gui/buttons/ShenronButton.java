package com.hmc.zenkai.client.gui.buttons;

import com.hmc.zenkai.client.gui.ZenkaiPalette;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Botón del pie de las pantallas de deseos de Shenlong. Los PanelButton (btn_wide, naranja/beige) desentonaban
 * sobre el verde dragón del panel, así que este se dibuja con la paleta SHENRON_*: relleno oscuro, borde de dos
 * tonos y esquinas recortadas 1 px como el marco del panel. Todo es simétrico respecto al eje del botón (sin
 * cinta lateral, a diferencia de las filas de la lista) para que el par Volver/Confirmar lo sea también.
 * Hereda de PanelButton para que ZenkaiPanelScreen siga tipando confirmButton/backButton igual.
 */
public class ShenronButton extends PanelButton {

    private final Kind kind;

    public ShenronButton(int x, int y, int w, int h, Component message, Kind kind, Runnable onClick) {
        super(x, y, w, h, message, kind, onClick);
        this.kind = kind;
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        boolean hovered = this.active && isMouseOver(mouseX, mouseY);
        boolean primary = kind == Kind.PRIMARY;
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();

        int outline = ZenkaiPalette.SHENRON_BORDER_IN;
        int rim = !active ? 0xFF16402A : hovered ? ZenkaiPalette.SHENRON_BORDER_HI
                : primary ? ZenkaiPalette.SHENRON_BORDER_OUT : ZenkaiPalette.SHENRON_BORDER_MID;
        int fill = !active ? 0xFF0C1913 : hovered ? 0xFF1B4A31 : primary ? 0xFF164029 : ZenkaiPalette.SHENRON_PANEL;

        // Contorno exterior, aro y relleno, cada uno 1 px más adentro; las 4 esquinas se saltan el píxel del contorno.
        g.fill(x + 1, y, x + w - 1, y + h, outline);
        g.fill(x, y + 1, x + w, y + h - 1, outline);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, rim);
        g.fill(x + 2, y + 2, x + w - 2, y + h - 2, fill);
        // Brillo superior de 1 px, igual de ancho a ambos lados.
        if (active) g.fill(x + 2, y + 2, x + w - 2, y + 3, hovered ? 0x40FFFFFF : 0x22FFFFFF);

        int color = !active ? ZenkaiPalette.SHENRON_MUTED
                : hovered ? ZenkaiPalette.TEXT_HOVER
                : primary ? ZenkaiPalette.SHENRON_TEXT : ZenkaiPalette.SHENRON_MUTED;
        var font = Minecraft.getInstance().font;
        g.drawString(font, getMessage(), x + (w - font.width(getMessage())) / 2, y + (h - 8) / 2 + 1, color, true);
    }
}
