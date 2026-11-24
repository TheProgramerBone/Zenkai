package com.hmc.zenkai.client.gui;

import com.hmc.zenkai.Zenkai;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * Cabecera de las pantallas de máquina: placa oscura AL ANCHO DEL TÍTULO + piloto suelto a su derecha (opción B elegida
 * por el usuario el 2026-11-23 sobre una maqueta con capturas reales). Antes la placa venía horneada en cada PNG con 134 px
 * fijos y cada máquina ponía el LED donde caía (pegado al texto, en medio de la placa, al final o fuera): cuatro sitios
 * distintos. tools/gen_tech_title_plate.py quitó la placa de los PNG y el aro del LED.
 *
 * Reglas del usuario: el LED va FUERA de la placa, SIN contorno (solo el núcleo de 10×10 de tech_led.png) y centrado en
 * vertical con la placa (núcleo en las filas 6..15 de una placa 3..18). Coordenadas LOCALES del panel; quien llama ya
 * trasladó al origen del panel (renderLabels) o suma su propio origen.
 */
public final class TechHeader {
    private TechHeader() {}

    public static final ResourceLocation LED =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/screens/tech_led.png");

    /** Placa: esquina superior izquierda, alto, y dónde va el texto (== la placa horneada que había antes). */
    public static final int PLATE_X = 9, PLATE_Y = 3, PLATE_H = 16, TITLE_X = 12, TITLE_Y = 7;
    /** Hueco de chasis entre la placa y el núcleo del LED. */
    public static final int LED_GAP = 3;
    /** Celda del atlas (16×16) y núcleo visible dentro de ella (10×10 desde (3,3)). */
    public static final int LED_CELL = 16, LED_CORE = 10, LED_CORE_OFF = 3;

    // Colores medidos en la placa horneada original (energy_generator.png).
    private static final int BORDER = 0xFF0A0C10, FILL = 0xFF12161E, SCAN = 0xFF1A202C;

    /** Título en mayúsculas y negrita (el de siempre de la familia). */
    public static Component styled(Component title) {
        return Component.literal(title.getString().toUpperCase(Locale.ROOT)).withStyle(ChatFormatting.BOLD);
    }

    /** Última columna (incluida) de la placa para ese título: 2 px de relleno a cada lado del texto, como a la izquierda. */
    public static int plateRight(Font font, Component styled) {
        return TITLE_X + font.width(styled) + 1;
    }

    /** X del núcleo visible del LED (para tooltips): pegado a la placa con {@link #LED_GAP} de chasis. */
    public static int ledCoreX(Font font, Component styled) {
        return plateRight(font, styled) + 1 + LED_GAP;
    }

    /** Y del núcleo visible: centrado en la placa. */
    public static int ledCoreY() {
        return PLATE_Y + (PLATE_H - LED_CORE) / 2;
    }

    /**
     * Dibuja placa, título y (si {@code led} no es null) el piloto encendido o apagado. Devuelve la x siguiente libre
     * (tras el LED, o tras la placa si no hay LED), por si la pantalla quiere colocar algo detrás.
     */
    public static int draw(GuiGraphics g, Font font, Component title, @Nullable Boolean led) {
        Component s = styled(title);
        int x1 = plateRight(font, s), y1 = PLATE_Y + PLATE_H - 1;
        g.fill(PLATE_X, PLATE_Y, x1 + 1, y1 + 1, BORDER);
        g.fill(PLATE_X + 1, PLATE_Y + 1, x1, y1, FILL);
        for (int row = PLATE_Y + 3; row < y1; row += 4) g.fill(PLATE_X + 2, row, x1 - 1, row + 1, SCAN);
        PanelText.onDark(g, font, s, TITLE_X, TITLE_Y, ZenkaiTechPalette.TITLE);
        if (led == null) return x1 + 1;
        int cx = ledCoreX(font, s), cy = ledCoreY();
        RenderSystem.enableBlend();
        g.setColor(1f, 1f, 1f, 1f);
        g.blit(LED, cx - LED_CORE_OFF, cy - LED_CORE_OFF, led ? LED_CELL : 0, 0, LED_CELL, LED_CELL, LED_CELL * 2, LED_CELL);
        return cx + LED_CORE;
    }

    /** ¿El ratón (local) está sobre el núcleo del LED? */
    public static boolean overLed(Font font, Component title, int lmx, int lmy) {
        Component s = styled(title);
        int cx = ledCoreX(font, s), cy = ledCoreY();
        return lmx >= cx && lmx < cx + LED_CORE && lmy >= cy && lmy < cy + LED_CORE;
    }
}
