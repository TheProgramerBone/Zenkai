package com.hmc.zenkai.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.Locale;

/**
 * Barra de recurso: marco + fondo + relleno, opcionalmente con etiqueta y valor.
 *
 * Hasta ahora había tres implementaciones distintas del mismo dibujo — la de alineamiento en
 * StatsScreen, la de maestría en MasteryScreen y la del scrollbar de Skills — con alturas,
 * marcos y colores de fondo diferentes. Aquí se define una sola vez para que Body, Stamina, Ki,
 * Ki Control y maestría se lean como el mismo objeto de interfaz.
 *
 * El marco NO es opcional a propósito: sobre el beige del panel una barra sin marco pierde el
 * borde inferior cuando el relleno está casi lleno, y el jugador no distingue 95 % de 100 %.
 */
public final class StatBar {
    private StatBar() {}

    /** Alto estándar de las barras de recurso. Constante para que no diverjan por pantalla. */
    public static final int H = 6;
    /** Alto de las barras finas (maestría, progreso dentro de una fila de lista). */
    public static final int H_THIN = 3;

    /** Barra desnuda: marco de 1 px + fondo + relleno. Devuelve el alto total ocupado. */
    public static int draw(GuiGraphics g, int x, int y, int w, int h,
                           double value, double max, int fillColor) {
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, ZenkaiPalette.BAR_FRAME);
        g.fill(x, y, x + w, y + h, ZenkaiPalette.BAR_BG);

        float ratio = (max <= 0) ? 0f : Mth.clamp((float) (value / max), 0f, 1f);
        int filled = Math.round(w * ratio);
        // Un recurso con algo dentro SIEMPRE enseña al menos 1 px: si no, 1/500 de ki se ve
        // idéntico a 0 y el jugador cree que la barra está vacía cuando aún puede lanzar algo.
        if (filled == 0 && value > 0) filled = 1;
        if (filled > 0) g.fill(x, y, x + filled, y + h, fillColor);
        return h + 2;
    }

    /**
     * Barra con etiqueta encima y "actual/máximo" alineado a la derecha.
     * Devuelve el alto total consumido, para que el llamante apile sin contar píxeles.
     */
    public static int drawLabeled(GuiGraphics g, Font font, int x, int y, int w,
                                  Component label, double value, double max, int fillColor) {
        g.drawString(font, label, x, y, ZenkaiPalette.LABEL_ON_PANEL, false);

        Component amount = Component.literal(fmt(value) + "/" + fmt(max));
        g.drawString(font, amount, x + w - font.width(amount), y, ZenkaiPalette.BODY_ON_PANEL, false);

        int barY = y + font.lineHeight + 1;
        draw(g, x, barY, w, H, value, max, fillColor);
        return (barY + H + 2) - y;
    }

    /**
     * Barra con etiqueta y porcentaje (Ki Control, maestría). Distinta de drawLabeled porque
     * "50 %" y "220/440" no son el mismo dato: el primero es una decisión del jugador y el
     * segundo un consumible.
     */
    public static int drawPercent(GuiGraphics g, Font font, int x, int y, int w,
                                  Component label, double percent, int fillColor) {
        g.drawString(font, label, x, y, ZenkaiPalette.LABEL_ON_PANEL, false);

        Component pct = Component.literal(Math.round(percent) + "%");
        g.drawString(font, pct, x + w - font.width(pct), y, ZenkaiPalette.BODY_ON_PANEL, false);

        int barY = y + font.lineHeight + 1;
        draw(g, x, barY, w, H, percent, 100.0, fillColor);
        return (barY + H + 2) - y;
    }

    /** Barra fina sin texto, para meter dentro de una fila de lista. */
    public static void thin(GuiGraphics g, int x, int y, int w, double value, double max, int fill) {
        g.fill(x, y, x + w, y + H_THIN, ZenkaiPalette.BAR_BG);
        float ratio = (max <= 0) ? 0f : Mth.clamp((float) (value / max), 0f, 1f);
        int filled = Math.round(w * ratio);
        if (filled > 0) g.fill(x, y, x + filled, y + H_THIN, fill);
    }

    /** Sin decimales si el número es entero: "440" y no "440.0" en una barra de ki. */
    private static String fmt(double d) {
        return (d == Math.rint(d)) ? String.valueOf((long) d) : String.format(Locale.ROOT, "%.1f", d);
    }
}