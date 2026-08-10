package com.hmc.zenkai.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
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

    /**
     * Fila compacta: etiqueta a la izquierda, barra en medio, valor pegado a la derecha.
     * Es el formato del panel principal — cabe en una línea de texto por recurso.
     */
    public static void row(GuiGraphics g, Font font, int labelX, int barX, int barW,
                           int rightEdge, int y, Component label,
                           double value, double max, int fillColor) {
        g.drawString(font, label, labelX, y, ZenkaiPalette.LABEL_ON_PANEL, false);

        // La barra se centra sobre la línea de texto: con lineHeight 9 y alto 4, +2 la deja
        // ópticamente alineada con las mayúsculas de la etiqueta.
        draw(g, barX, y + 2, barW, H, value, max, fillColor);

        Component amount = Component.literal(fmt(value) + "/" + fmt(max));
        g.drawString(font, amount, rightEdge - font.width(amount), y,
                ZenkaiPalette.BODY_ON_PANEL, false);
    }

    /** Barra fina sin marco, para meter dentro de una fila de lista. */
    public static void thin(GuiGraphics g, int x, int y, int w, double value, double max, int fill) {
        g.fill(x, y, x + w, y + H_THIN, ZenkaiPalette.BAR_BG_DARK);
        float ratio = (max <= 0) ? 0f : Mth.clamp((float) (value / max), 0f, 1f);
        int filled = Math.round(w * ratio);
        if (filled > 0) g.fill(x, y, x + filled, y + H_THIN, fill);
    }

    /** Sin decimales si el número es entero: "440" y no "440.0" en una barra de ki. */
    private static String fmt(double d) {
        return (d == Math.rint(d)) ? String.valueOf((long) d) : String.format(Locale.ROOT, "%.1f", d);
    }
}