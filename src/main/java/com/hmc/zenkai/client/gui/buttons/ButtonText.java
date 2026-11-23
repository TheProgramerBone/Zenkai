package com.hmc.zenkai.client.gui.buttons;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Texto de botón SIEMPRE centrado (regla del usuario, 2026-11-22), en un solo sitio para que cada botón lo haga igual.
 *
 * Se centra la TINTA, no la caja de la fuente: {@code font.width} cuenta 1 px de hueco tras la última letra y las
 * mayúsculas ocupan 7 filas, no 8. La cuenta antigua ({@code ancho / 2 − texto / 2}, {@code (alto − 8) / 2}) dejaba en un
 * botón de 13 px 2 filas arriba y 4 abajo, y podía torcer 1 px a la izquierda. Si el texto no cabe se encoge en vez de
 * pisar el bisel.
 */
public final class ButtonText {
    /** Margen mínimo por lado: bisel de 2 px + 1 px de aire. */
    private static final int INSET = 3;

    private ButtonText() {}

    public static void drawCentered(GuiGraphics g, Font font, Component msg, int x, int y, int w, int h,
                                    int color, boolean shadow) {
        int inkW = Math.max(0, font.width(msg) - 1);
        int maxW = w - INSET * 2;
        if (inkW <= maxW) {
            g.drawString(font, msg, x + (w - inkW) / 2, y + (h - 7) / 2, color, shadow);
            return;
        }
        float sc = maxW / (float) inkW;
        g.pose().pushPose();
        g.pose().translate(x + INSET, y + (h - 7 * sc) / 2f, 0);
        g.pose().scale(sc, sc, 1f);
        g.drawString(font, msg, 0, 0, color, shadow);
        g.pose().popPose();
    }
}
