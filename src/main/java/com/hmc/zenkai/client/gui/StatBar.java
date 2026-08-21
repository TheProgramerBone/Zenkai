package com.hmc.zenkai.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import com.hmc.zenkai.util.ZenkaiNumbers;
import net.minecraft.util.Mth;

import java.util.Locale;

/**
 * Barra de recurso: marco + canal + relleno, opcionalmente con etiqueta y valor.
 * Había tres implementaciones del mismo dibujo (alineamiento en StatsScreen, maestría en
 * MasteryScreen, scrollbar de Skills) con alturas, marcos y fondos distintos. Aquí se define
 * una vez para que Body, Stamina, Ki, Ki Control y maestría se lean como el mismo objeto.
 * DOS CONTEXTOS, y no dan lo mismo: sobre el beige del panel el marco es el marrón del borde y
 * el canal un beige hundido; sobre un popup oscuro el canal es negro translúcido. Con un solo
 * juego de colores, la versión de panel abría un agujero negro en el beige y la de popup se
 * perdía sobre el fondo.
 * El marco NO es opcional: sin él una barra casi llena pierde el borde derecho contra el fondo
 * y el jugador no distingue 95 % de 100 %.
 */
public final class StatBar {
    private StatBar() {}

    /** Alto de las barras de recurso del panel principal. Finas a propósito: acompañan a los
     *  atributos, no compiten con ellos. */
    public static final int H = 4;
    /** Alto de las barras protagonistas (Ki Control, popups). */
    public static final int H_WIDE = 6;
    /** Alto de las barras de progreso dentro de una fila de lista. */
    public static final int H_THIN = 3;

    /** Barra sobre el BEIGE del panel. */
    public static int draw(GuiGraphics g, int x, int y, int w, int h,
                           double value, double max, int fillColor) {
        return draw(g, x, y, w, h, value, max, fillColor, ZenkaiPalette.BAR_BG);
    }

    /** Barra sobre un fondo OSCURO (popup, tooltip). */
    public static int drawOnDark(GuiGraphics g, int x, int y, int w, int h,
                                 double value, double max, int fillColor) {
        return draw(g, x, y, w, h, value, max, fillColor, ZenkaiPalette.BAR_BG_DARK);
    }

    private static int draw(GuiGraphics g, int x, int y, int w, int h,
                            double value, double max, int fillColor, int channel) {
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, ZenkaiPalette.BAR_FRAME);
        g.fill(x, y, x + w, y + h, channel);

        float ratio = (max <= 0) ? 0f : Mth.clamp((float) (value / max), 0f, 1f);
        int filled = Math.round(w * ratio);
        // Un recurso con algo dentro SIEMPRE enseña al menos 1 px: si no, 1/500 de ki se ve
        // idéntico a 0 y el jugador cree que la barra está vacía cuando aún puede lanzar algo.
        if (filled == 0 && value > 0) filled = 1;
        if (filled > 0) g.fill(x, y, x + filled, y + h, fillColor);
        return h + 2;
    }

    /** Hueco mínimo entre el final de la barra y el arranque del texto. */
    private static final int ROW_GAP = 3;
    /** Por debajo de esto la barra ya no aporta nada legible: mejor cederle todo el ancho al
     *  número que dejar una barra de dos píxeles que nadie puede leer. */
    private static final int ROW_MIN_BAR_W = 10;

    /**
     * Fila compacta: etiqueta a la izquierda, barra en medio, valor pegado a la derecha.
     * Es el formato del panel principal — cabe en una línea de texto por recurso.
         * El ancho de la barra se calcula AQUÍ, después de medir el texto — no lo fija el
     * llamador de antemano. Con un hueco fijo reservado para el texto ("15.6M/15.6M" ya no
     * cabe en 50px que sobraban de sobra con "5M/5M"), el texto empezaba antes de donde
     * terminaba la barra y las dos capas se solapaban en cuanto el número crecía. Midiendo el
     * texto primero, la barra se encoge sola y nunca invade la columna de números, sin importar
     * cuántas cifras tenga.
     */
    public static void row(GuiGraphics g, Font font, int labelX, int barX,
                           int rightEdge, int y, Component label,
                           double value, double max, int fillColor) {
        g.drawString(font, label, labelX, y, ZenkaiPalette.LABEL_ON_PANEL, false);

        // SIEMPRE compacto: "6.8M/6.8M" en vez de "6800010/6800010". Con siete cifras por
        // lado el texto invadía la barra y se leían encima el uno del otro (visible en juego
        // con un personaje de PL alto). El valor exacto está en el tooltip, no aquí: esta línea
        // sirve para ver de un vistazo cuánto queda, no para auditar el número.
        Component amount = Component.literal(
                ZenkaiNumbers.format(Math.round(value)) + "/" + ZenkaiNumbers.format(Math.round(max)));
        int amountW = font.width(amount);
        g.drawString(font, amount, rightEdge - amountW, y, ZenkaiPalette.BODY_ON_PANEL, false);

        // La barra se centra sobre la línea de texto: con lineHeight 9 y alto 4, +2 la deja
        // ópticamente alineada con las mayúsculas de la etiqueta.
        int barW = Math.max(ROW_MIN_BAR_W, (rightEdge - amountW - ROW_GAP) - barX);
        draw(g, barX, y + 2, barW, H, value, max, fillColor);
    }

    /** Barra fina sin marco, para meter dentro de una fila de lista. */
    public static void thin(GuiGraphics g, int x, int y, int w, double value, double max, int fill) {
        g.fill(x, y, x + w, y + H_THIN, ZenkaiPalette.BAR_BG_DARK);
        float ratio = (max <= 0) ? 0f : Mth.clamp((float) (value / max), 0f, 1f);
        int filled = Math.round(w * ratio);
        if (filled > 0) g.fill(x, y, x + filled, y + H_THIN, fill);
    }

    /** Sin decimales si el número es entero: "440" y no "440.0". Lo usa drawLabeled. */
    private static String fmt(double d) {
        return (d == Math.rint(d)) ? String.valueOf((long) d) : String.format(Locale.ROOT, "%.1f", d);
    }
}