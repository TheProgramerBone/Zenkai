package com.hmc.zenkai.client.gui;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * Título de pantalla: MAYÚSCULAS + negrita + sombra, en el dorado del mod, dibujado FUERA
 * del panel (justo encima). Único sitio donde se define el look de los títulos.
 *
 * Fuera y no dentro por dos razones: no come espacio útil del panel — ninguna pantalla ha
 * tenido que recolocar nada — y sobre el fondo oscuro del mundo la sombra rinde mucho mejor
 * que sobre el beige de common_screen.
 *
 * Se descartó una fuente bitmap propia: en mayúsculas el resultado sería casi idéntico a la
 * vanilla y costaría mantener un atlas.
 */
public final class ScreenTitle {

    /** Dorado cálido del mod. */
    public static final int COLOR = 0xFFFFC94A;

    /** Píxeles por encima del borde superior del panel. 14 = ~5 de aire con lineHeight 9. */
    public static final int ABOVE = 14;

    private ScreenTitle() {}

    /**
     * Primer Y utilizable dentro del panel, relativo a su borde superior. El título va FUERA
     * (drawAbovePanel), así que aquí NO hay que reservarle sitio: los 30-50 px que tenían las
     * pantallas eran el hueco del título cuando se dibujaba dentro, y cada una eligió el suyo.
     * Un solo número para todas, o vuelven a divergir en la siguiente pantalla.
     */
    public static final int CONTENT_TOP = 16;

    /**
     * Lo que llaman las screens: centrado en cx, justo encima de panelTop.
     * El clamp evita que se salga por arriba en ventanas bajas o con GUI Scale alto.
     */
    public static void drawAbovePanel(GuiGraphics g, Font font, Component title, int cx, int panelTop) {
        drawCentered(g, font, title, cx, Math.max(2, panelTop - ABOVE));
    }

    /** drawCenteredString no admite el flag de sombra, por eso se centra a mano. */
    public static void drawCentered(GuiGraphics g, Font font, Component title, int cx, int y) {
        drawCentered(g, font, title, cx, y, COLOR);
    }

    public static void drawCentered(GuiGraphics g, Font font, Component title, int cx, int y, int color) {
        // Se mide el componente YA estilizado: la negrita suma 1 px por glifo y si se midiera
        // el texto plano el centrado saldría desviado a la derecha.
        Component styled = styled(title);
        g.drawString(font, styled, cx - font.width(styled) / 2, y, color, true);
    }

    /** Locale.ROOT y no el del jugador: en turco la "i" mayúscula es "İ" y rompe el texto. */
    public static Component styled(Component c) {
        return Component.literal(c.getString().toUpperCase(Locale.ROOT))
                .withStyle(ChatFormatting.BOLD);
    }
}