package com.hmc.zenkai.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/**
 * Ajuste de renglones de los tooltips (2026-11-24, pedido del usuario: un texto largo nunca debe salirse de la pantalla;
 * cada línea decide sola si necesita uno o varios renglones). Vanilla solo parte por sí mismo los tooltips de widgets
 * ({@code Tooltip.create}, 170 px) y los de ítem al ancho de la pantalla; las rutas {@code g.renderTooltip(font, Component, ..)}
 * y los tooltips propios de TechPanelScreen no partían nada y una frase larga salía por el borde (captura de la Infusora).
 *
 * Tres puntos de entrada, todos con el MISMO ancho máximo ({@link #maxWidth}):
 *   - {@link #render}: sustituto de {@code g.renderTooltip(font, Component, x, y)} y de la versión con lista;
 *   - {@code TechPanelScreen.drawTooltip}: usa {@link #maxWidth} y {@code font.split} para sus filas con icono;
 *   - {@code client/TooltipWrapHandler}: fija ese ancho en los tooltips de ítems del mod y en cualquier
 *     {@code renderComponentTooltip} hecho desde una pantalla del mod.
 */
public final class TooltipText {
    /** Ancho máximo de un renglón de tooltip, en píxeles de GUI. */
    public static final int MAX_W = 220;
    /** Margen mínimo con el borde de la pantalla. */
    private static final int MARGIN = 24;

    private TooltipText() {}

    /** Ancho útil de un renglón: {@link #MAX_W}, o menos si la pantalla es estrecha. */
    public static int maxWidth(int screenWidth) {
        return Math.max(60, Math.min(MAX_W, screenWidth - MARGIN));
    }

    /** Parte cada componente en los renglones que necesite (uno si cabe) conservando su estilo. */
    public static List<FormattedCharSequence> wrap(Font font, List<? extends Component> lines, int screenWidth) {
        int w = maxWidth(screenWidth);
        List<FormattedCharSequence> out = new ArrayList<>();
        for (Component c : lines) {
            List<FormattedCharSequence> parts = font.split(c, w);
            if (parts.isEmpty()) out.add(FormattedCharSequence.EMPTY);   // una línea en blanco se conserva
            else out.addAll(parts);
        }
        return out;
    }

    /** Pinta un tooltip de un texto, partido si hace falta. */
    public static void render(GuiGraphics g, Font font, Component text, int x, int y) {
        render(g, font, List.of(text), x, y);
    }

    /** Pinta un tooltip de varias líneas, cada una partida si hace falta. */
    public static void render(GuiGraphics g, Font font, List<? extends Component> lines, int x, int y) {
        if (lines.isEmpty()) return;
        g.renderTooltip(font, wrap(font, lines, g.guiWidth()), x, y);
    }
}
