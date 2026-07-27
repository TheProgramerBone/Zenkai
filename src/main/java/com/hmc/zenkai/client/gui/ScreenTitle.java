package com.hmc.zenkai.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * Título de pantalla: MAYÚSCULAS, color propio del mod y sombra. Único sitio donde se define
 * el look de los títulos — si mañana cambia el color o se quiere una fuente bitmap, se toca
 * aquí y no en las once pantallas.
 *
 * Se descartó la fuente bitmap propia: el resultado habría sido casi idéntico a la vanilla
 * en mayúsculas, y esto no cuesta assets.
 */
public final class ScreenTitle {

    /** Dorado cálido: contrasta sobre el beige de common_screen y con la sombra se despega. */
    public static final int COLOR = 0xFFFFC94A;

    private ScreenTitle() {}

    /** Centrado en cx. drawCenteredString no admite sombra, por eso se centra a mano. */
    public static void drawCentered(GuiGraphics g, Font font, Component title, int cx, int y) {
        drawCentered(g, font, title, cx, y, COLOR);
    }

    public static void drawCentered(GuiGraphics g, Font font, Component title, int cx, int y, int color) {
        Component up = upper(title);
        g.drawString(font, up, cx - font.width(up) / 2, y, color, true);
    }

    /** Alineado a la izquierda desde x. */
    public static void draw(GuiGraphics g, Font font, Component title, int x, int y) {
        g.drawString(font, upper(title), x, y, COLOR, true);
    }

    /** Locale.ROOT y no el del jugador: con locale turco, "i" mayúscula es "İ" y rompe el texto. */
    public static Component upper(Component c) {
        return Component.literal(c.getString().toUpperCase(Locale.ROOT));
    }
}