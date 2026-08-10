package com.hmc.zenkai.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * Dibujo de texto CON la regla de sombra ya aplicada. Un helper por contexto, no un parámetro.
 * POR QUÉ EXISTE: GuiGraphics#drawCenteredString dibuja SIEMPRE con sombra — esa firma no tiene
 * parámetro para desactivarla. Cada vez que se centraba un texto sobre el panel beige entraba
 * una sombra negra bajo un color tierra, y el resultado es un borrón. La regla estaba escrita
 * en ZenkaiPalette y aun así se coló en cuatro pantallas, porque cumplirla exigía acordarse de
 * no usar el procedimiento obvio.
 * Aquí el procedimiento obvio hace lo correcto: onPanel() nunca lleva sombra, onDark() siempre. No se
 * puede equivocar uno eligiendo mal el color, solo eligiendo mal el fondo — y el fondo se sabe
 * mirando la pantalla.
 * Los *Right() y *Centered() calculan la x ellos mismos, que es la otra mitad del motivo por el
 * que se acababa recurriendo a drawCenteredString.
 */
public final class PanelText {
    private PanelText() {}

    // ── Sobre el beige del panel: SIN sombra ─────────────────────────────────

    public static void onPanel(GuiGraphics g, Font font, Component text, int x, int y, int color) {
        g.drawString(font, text, x, y, color, false);
    }

    public static void onPanel(GuiGraphics g, Font font, String text, int x, int y, int color) {
        g.drawString(font, text, x, y, color, false);
    }

    public static void onPanel(GuiGraphics g, Font font, FormattedCharSequence text,
                               int x, int y, int color) {
        g.drawString(font, text, x, y, color, false);
    }

    /** Centrado en cx. Sustituto directo de drawCenteredString sobre el panel. */
    public static void centeredOnPanel(GuiGraphics g, Font font, Component text,
                                       int cx, int y, int color) {
        g.drawString(font, text, cx - font.width(text) / 2, y, color, false);
    }

    /** Alineado a la derecha, terminando en rightX. */
    public static void rightOnPanel(GuiGraphics g, Font font, Component text,
                                    int rightX, int y, int color) {
        g.drawString(font, text, rightX - font.width(text), y, color, false);
    }

    // ── Sobre fondo oscuro (popup, tooltip, HUD, fuera del panel): CON sombra ─

    public static void onDark(GuiGraphics g, Font font, Component text, int x, int y, int color) {
        g.drawString(font, text, x, y, color, true);
    }

    public static void centeredOnDark(GuiGraphics g, Font font, Component text,
                                      int cx, int y, int color) {
        g.drawString(font, text, cx - font.width(text) / 2, y, color, true);
    }

    public static void rightOnDark(GuiGraphics g, Font font, Component text,
                                   int rightX, int y, int color) {
        g.drawString(font, text, rightX - font.width(text), y, color, true);
    }

    // ── Recorte ──────────────────────────────────────────────────────────────

    /**
     * Recorta a una línea con puntos suspensivos si no cabe en maxW.
         * Devuelve el texto tal cual cuando cabe, así que llamarlo siempre no cuesta nada. El
     * criterio de recorte estaba copiado en tres pantallas con tres anchos distintos para los
     * puntos.
     */
    public static Component fit(Font font, Component text, int maxW) {
        if (maxW <= 0) return Component.empty();
        if (font.width(text) <= maxW) return text;
        int dots = font.width("...");
        String cut = font.plainSubstrByWidth(text.getString(), Math.max(0, maxW - dots));
        return Component.literal(cut + "...");
    }

    /** ¿Este texto necesitaría recorte? Sirve para decidir si hace falta tooltip. */
    public static boolean overflows(Font font, Component text, int maxW) {
        return font.width(text) > maxW;
    }
}