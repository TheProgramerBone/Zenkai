package com.hmc.zenkai.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;

/**
 * Cómo se DIBUJA el nombre de una tecla en un espacio pequeño (la celda de un slot de combate).
 * El nombre traducido de vainilla ("Left Shift", "Mouse Button 4", "Up Arrow") no cabe, y recortarlo
 * da cosas ilegibles ("Lef"). Aquí las teclas especiales tienen representación propia, como en un
 * minijuego de ritmo:
 *  - flechas: un icono de flecha dibujado a mano (arriba, abajo, izquierda, derecha);
 *  - Shift: la letra del lado (L / R) y una flecha hacia arriba (⇧);
 *  - Ctrl/Alt: lado + abreviatura ("LCt", "RAl");
 *  - botones de ratón: "M1", "M4"...;
 *  - teclas de texto largo conocidas: abreviaturas fijas ("Spc", "Tab", "Ent"...);
 *  - cualquier otra: su nombre, recortado a MAX_CHARS.
 * Es solo presentación: la tecla real sigue siendo la del KeyMapping.
 */
public final class KeyLabel {
    private KeyLabel() {}

    private static final int MAX_CHARS = 3;
    /** Lado de los iconos de flecha, en píxeles. */
    private static final int ARROW = 5;
    private static final int GAP = 1;

    private enum Dir { UP, DOWN, LEFT, RIGHT }

    /** Dibuja la etiqueta de `key` con su esquina superior izquierda en (x, y). */
    public static void draw(GuiGraphics g, Font font, KeyMapping key, int x, int y, int color) {
        if (key == null || key.isUnbound()) { return; }
        InputConstants.Key k = key.getKey();
        int code = k.getValue();

        if (k.getType() == InputConstants.Type.KEYSYM) {
            Dir dir = arrowOf(code);
            if (dir != null) {
                drawArrow(g, x, y + 1, dir, color);
                return;
            }
            if (code == GLFW.GLFW_KEY_LEFT_SHIFT || code == GLFW.GLFW_KEY_RIGHT_SHIFT) {
                String side = code == GLFW.GLFW_KEY_LEFT_SHIFT ? "L" : "R";
                g.drawString(font, side, x, y, color, true);
                drawArrow(g, x + font.width(side) + GAP, y + 1, Dir.UP, color);
                return;
            }
        }
        g.drawString(font, text(key), x, y, color, true);
    }

    /** Ancho en píxeles de lo que dibuja draw() (para centrar o alinear). */
    public static int width(Font font, KeyMapping key) {
        if (key == null || key.isUnbound()) return 0;
        InputConstants.Key k = key.getKey();
        int code = k.getValue();
        if (k.getType() == InputConstants.Type.KEYSYM) {
            if (arrowOf(code) != null) return ARROW;
            if (code == GLFW.GLFW_KEY_LEFT_SHIFT || code == GLFW.GLFW_KEY_RIGHT_SHIFT) {
                return font.width(code == GLFW.GLFW_KEY_LEFT_SHIFT ? "L" : "R") + GAP + ARROW;
            }
        }
        return font.width(text(key));
    }

    private static Dir arrowOf(int code) {
        return switch (code) {
            case GLFW.GLFW_KEY_UP -> Dir.UP;
            case GLFW.GLFW_KEY_DOWN -> Dir.DOWN;
            case GLFW.GLFW_KEY_LEFT -> Dir.LEFT;
            case GLFW.GLFW_KEY_RIGHT -> Dir.RIGHT;
            default -> null;
        };
    }

    /** Texto corto para cuanto no es una flecha ni un Shift. */
    static String text(KeyMapping key) {
        InputConstants.Key k = key.getKey();
        int code = k.getValue();
        if (k.getType() == InputConstants.Type.MOUSE) return "M" + (code + 1);
        if (k.getType() == InputConstants.Type.KEYSYM) {
            switch (code) {
                case GLFW.GLFW_KEY_LEFT_CONTROL: return "LCt";
                case GLFW.GLFW_KEY_RIGHT_CONTROL: return "RCt";
                case GLFW.GLFW_KEY_LEFT_ALT: return "LAl";
                case GLFW.GLFW_KEY_RIGHT_ALT: return "RAl";
                case GLFW.GLFW_KEY_SPACE: return "Spc";
                case GLFW.GLFW_KEY_TAB: return "Tab";
                case GLFW.GLFW_KEY_ENTER: return "Ent";
                case GLFW.GLFW_KEY_BACKSPACE: return "Bks";
                case GLFW.GLFW_KEY_CAPS_LOCK: return "Cap";
                case GLFW.GLFW_KEY_ESCAPE: return "Esc";
                case GLFW.GLFW_KEY_DELETE: return "Del";
                case GLFW.GLFW_KEY_INSERT: return "Ins";
                case GLFW.GLFW_KEY_HOME: return "Hom";
                case GLFW.GLFW_KEY_END: return "End";
                case GLFW.GLFW_KEY_PAGE_UP: return "PgU";
                case GLFW.GLFW_KEY_PAGE_DOWN: return "PgD";
                default: break;
            }
            // Teclado numérico: "Keypad 5" -> "N5".
            if (code >= GLFW.GLFW_KEY_KP_0 && code <= GLFW.GLFW_KEY_KP_9) {
                return "N" + (code - GLFW.GLFW_KEY_KP_0);
            }
        }
        String s = key.getTranslatedKeyMessage().getString();
        return s.length() <= MAX_CHARS ? s : s.substring(0, MAX_CHARS);
    }

    // ── Flechas: 5x5 píxeles, dibujadas rectángulo a rectángulo ────────────────────────────────

    /** Flecha hacia arriba (la base); las otras tres son esta misma girada. */
    private static final String[] UP = {
            "..#..",
            ".###.",
            "#####",
            "..#..",
            "..#..",
    };

    private static void drawArrow(GuiGraphics g, int x, int y, Dir dir, int color) {
        // Sombra de 1 px, como la del texto con sombra de vainilla, para que se lea sobre cualquier fondo.
        drawArrowPixels(g, x + 1, y + 1, dir, 0xFF202020);
        drawArrowPixels(g, x, y, dir, color);
    }

    private static void drawArrowPixels(GuiGraphics g, int x, int y, Dir dir, int color) {
        for (int row = 0; row < ARROW; row++) {
            for (int col = 0; col < ARROW; col++) {
                // Coordenadas del píxel en la plantilla "arriba" según la rotación.
                int r, c;
                switch (dir) {
                    case DOWN -> { r = ARROW - 1 - row; c = col; }
                    case LEFT -> { r = col; c = row; }                   // transpuesta: punta a la izquierda
                    case RIGHT -> { r = ARROW - 1 - col; c = row; }       // punta a la derecha
                    default -> { r = row; c = col; }
                }
                if (UP[r].charAt(c) == '#') g.fill(x + col, y + row, x + col + 1, y + row + 1, color);
            }
        }
    }
}
