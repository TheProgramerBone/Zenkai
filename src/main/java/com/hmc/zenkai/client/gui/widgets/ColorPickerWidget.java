package com.hmc.zenkai.client.gui.widgets;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/**
 * Widget de selector de color HSV reutilizable.
 *
 * Contiene:
 *   - Cuadrado HSV (S en X, V en Y)
 *   - Barra de Hue (H) a la derecha del cuadrado
 *   - Cuadrado de preview del color actual
 *   - HEX input sincronizado bidireccionalmente
 *
 * Uso:
 *   ColorPickerWidget picker = new ColorPickerWidget(x, y, 0xFF33CCFF, color -> {
 *       myAttachment.setKiColor(color);
 *   });
 *   addRenderableWidget(picker);
 *   // Al hacer tick, picker.tick() para el EditBox interno
 */
public class ColorPickerWidget extends AbstractWidget {

    // ── Dimensiones internas ──────────────────────────────────────────────────
    public static final int SV_SIZE   = 80;  // cuadrado HSV
    public static final int HUE_W     = 10;  // barra de hue
    public static final int HUE_GAP   = 4;   // espacio entre cuadrado y barra
    public static final int PREVIEW_W = 20;  // cuadrado preview
    public static final int PREVIEW_H = 14;
    public static final int HEX_H     = 14;  // altura del HEX input
    public static final int HEX_GAP   = 4;   // espacio entre SV y HEX

    // Ancho total = SV_SIZE + HUE_GAP + HUE_W + HUE_GAP + PREVIEW_W
    public static final int TOTAL_W = SV_SIZE + HUE_GAP + HUE_W + HUE_GAP + PREVIEW_W;
    // Alto total = SV_SIZE + HEX_GAP + HEX_H
    public static final int TOTAL_H = SV_SIZE + HEX_GAP + HEX_H;

    // ── Estado HSV ────────────────────────────────────────────────────────────
    private float hue        = 0f;  // 0–360
    private float saturation = 1f;  // 0–1
    private float value      = 1f;  // 0–1 (brillo)

    // ── Subwidgets ────────────────────────────────────────────────────────────
    private final EditBox hexInput;

    // ── Drag state ────────────────────────────────────────────────────────────
    private boolean draggingSV  = false;
    private boolean draggingHue = false;

    // ── Callback ──────────────────────────────────────────────────────────────
    private final Consumer<Integer> onChange; // recibe ARGB 0xFF??????

    // ── Label ─────────────────────────────────────────────────────────────────
    private final String label; // ej: "Ki Color", "Hair Color"

    public ColorPickerWidget(int x, int y, int initialArgb, String label, Consumer<Integer> onChange) {
        super(x, y, TOTAL_W, TOTAL_H, Component.empty());
        this.label    = label;
        this.onChange = onChange;

        // Inicializar HSV desde el color dado
        int rgb = initialArgb & 0xFFFFFF;
        float[] hsv = rgbToHsv(rgb);
        this.hue        = hsv[0];
        this.saturation = hsv[1];
        this.value      = hsv[2];

        // HEX input — debajo del cuadrado SV
        int hexX = x;
        int hexY = y + SV_SIZE + HEX_GAP;
        int hexW = SV_SIZE + HUE_GAP + HUE_W; // mismo ancho que SV + barra hue
        this.hexInput = new EditBox(
                Minecraft.getInstance().font,
                hexX, hexY, hexW, HEX_H,
                Component.empty()
        );
        this.hexInput.setMaxLength(7);
        this.hexInput.setValue(String.format("#%06X", rgb));
        this.hexInput.setResponder(s -> {
            Integer parsed = parseHex(s);
            if (parsed != null) {
                float[] h = rgbToHsv(parsed);
                this.hue        = h[0];
                this.saturation = h[1];
                this.value      = h[2];
                fireChange();
            }
        });
    }

    // ── Render ────────────────────────────────────────────────────────────────
    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int x = getX();
        int y = getY();

        // Label encima
        if (label != null && !label.isEmpty()) {
            g.drawString(Minecraft.getInstance().font,
                    Component.literal(label),
                    x, y - 10, 0xC8A96E, false);
        }

        // ── Cuadrado SV ──────────────────────────────────────────────────────
        renderSVSquare(g, x, y);

        // Cursor en el cuadrado SV
        int svCurX = x + (int)(saturation * (SV_SIZE - 1));
        int svCurY = y + (int)((1f - value) * (SV_SIZE - 1));
        // Círculo de cursor (cruz 5x5)
        g.fill(svCurX - 2, svCurY,     svCurX + 3, svCurY + 1, 0xFFFFFFFF);
        g.fill(svCurX,     svCurY - 2, svCurX + 1, svCurY + 3, 0xFFFFFFFF);

        // ── Barra de Hue ─────────────────────────────────────────────────────
        int hueX = x + SV_SIZE + HUE_GAP;
        renderHueBar(g, hueX, y);

        // Cursor en la barra de hue
        int hueCurY = y + (int)(hue / 360f * (SV_SIZE - 1));
        g.fill(hueX - 1,       hueCurY,     hueX + HUE_W + 1, hueCurY + 1, 0xFFFFFFFF);

        // ── Preview ──────────────────────────────────────────────────────────
        int prevX = hueX + HUE_W + HUE_GAP;
        int prevY = y;
        int currentRgb = hsvToRgb(hue, saturation, value);
        g.fill(prevX - 1, prevY - 1, prevX + PREVIEW_W + 1, prevY + PREVIEW_H + 1, 0xFFFFFFFF);
        g.fill(prevX, prevY, prevX + PREVIEW_W, prevY + PREVIEW_H, 0xFF000000 | currentRgb);

        // ── HEX input ────────────────────────────────────────────────────────
        hexInput.render(g, mouseX, mouseY, partialTick);
    }

    /**
     * Renderiza el cuadrado SV pixel a pixel interpolando entre colores.
     * Para evitar llenar 80x80 = 6400 calls, dibujamos columnas verticales
     * (80 calls de fill de 1px de ancho).
     */
    private void renderSVSquare(GuiGraphics g, int x, int y) {
        for (int px = 0; px < SV_SIZE; px++) {
            float s = (float) px / (SV_SIZE - 1);
            // Por columna: de arriba (V=1) a abajo (V=0)
            for (int py = 0; py < SV_SIZE; py++) {
                float v = 1f - (float) py / (SV_SIZE - 1);
                int rgb = hsvToRgb(hue, s, v);
                g.fill(x + px, y + py, x + px + 1, y + py + 1, 0xFF000000 | rgb);
            }
        }
    }

    /**
     * Renderiza la barra de Hue (10px de ancho, SV_SIZE de alto).
     */
    private void renderHueBar(GuiGraphics g, int x, int y) {
        for (int py = 0; py < SV_SIZE; py++) {
            float h = (float) py / (SV_SIZE - 1) * 360f;
            int rgb = hsvToRgb(h, 1f, 1f);
            g.fill(x, y + py, x + HUE_W, y + py + 1, 0xFF000000 | rgb);
        }
    }

    // ── Input handling ────────────────────────────────────────────────────────
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (hexInput.mouseClicked(mx, my, button)) {
            hexInput.setFocused(true);
            return true;
        }
        // Si el click fue fuera del hexInput, quitar foco
        hexInput.setFocused(false);

        int x = getX();
        int y = getY();

        // ¿Click en cuadrado SV?
        if (mx >= x && mx < x + SV_SIZE && my >= y && my < y + SV_SIZE) {
            draggingSV = true;
            updateSV(mx, my);
            return true;
        }

        // ¿Click en barra de Hue?
        int hueX = x + SV_SIZE + HUE_GAP;
        if (mx >= hueX && mx < hueX + HUE_W && my >= y && my < y + SV_SIZE) {
            draggingHue = true;
            updateHue(my);
            return true;
        }

        return false;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (draggingSV)  { updateSV(mx, my);  return true; }
        if (draggingHue) { updateHue(my);     return true; }
        return false;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        draggingSV  = false;
        draggingHue = false;
        return false;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mod) {
        return hexInput.keyPressed(key, scan, mod);
    }

    @Override
    public boolean charTyped(char c, int mod) {
        return hexInput.charTyped(c, mod);
    }

    // ── Lógica interna ────────────────────────────────────────────────────────
    private void updateSV(double mx, double my) {
        int x = getX();
        int y = getY();
        saturation = (float) Math.max(0, Math.min(1, (mx - x) / (SV_SIZE - 1)));
        value      = 1f - (float) Math.max(0, Math.min(1, (my - y) / (SV_SIZE - 1)));
        syncHexFromHsv();
        fireChange();
    }

    private void updateHue(double my) {
        int y = getY();
        hue = (float) Math.max(0, Math.min(360, (my - y) / (SV_SIZE - 1) * 360f));
        syncHexFromHsv();
        fireChange();
    }

    private void syncHexFromHsv() {
        int rgb = hsvToRgb(hue, saturation, value);
        // Suprimir responder temporalmente para evitar loop
        hexInput.setResponder(null);
        hexInput.setValue(String.format("#%06X", rgb));
        hexInput.setResponder(s -> {
            Integer parsed = parseHex(s);
            if (parsed != null) {
                float[] h = rgbToHsv(parsed);
                this.hue        = h[0];
                this.saturation = h[1];
                this.value      = h[2];
                fireChange();
            }
        });
    }

    private void fireChange() {
        if (onChange != null) {
            onChange.accept(0xFF000000 | hsvToRgb(hue, saturation, value));
        }
    }

    // ── API pública ───────────────────────────────────────────────────────────
    /** Devuelve el color actual como ARGB opaco. */
    public int getArgb() {
        return 0xFF000000 | hsvToRgb(hue, saturation, value);
    }

    /** Actualiza el color programáticamente (ej. al cargar datos guardados). */
    public void setColor(int argb) {
        int rgb = argb & 0xFFFFFF;
        float[] hsv = rgbToHsv(rgb);
        this.hue        = hsv[0];
        this.saturation = hsv[1];
        this.value      = hsv[2];
        syncHexFromHsv();
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput out) {}

    // ── Conversión de color ───────────────────────────────────────────────────
    /** RGB → HSV. Devuelve float[]{H 0-360, S 0-1, V 0-1}. */
    public static float[] rgbToHsv(int rgb) {
        float r = ((rgb >> 16) & 0xFF) / 255f;
        float g = ((rgb >>  8) & 0xFF) / 255f;
        float b = ( rgb        & 0xFF) / 255f;

        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float delta = max - min;

        float h = 0f;
        if (delta > 0f) {
            if      (max == r) h = 60f * (((g - b) / delta) % 6f);
            else if (max == g) h = 60f * (((b - r) / delta) + 2f);
            else               h = 60f * (((r - g) / delta) + 4f);
        }
        if (h < 0f) h += 360f;

        float s = (max == 0f) ? 0f : delta / max;
        float v = max;

        return new float[]{ h, s, v };
    }

    /** HSV → RGB. H: 0-360, S: 0-1, V: 0-1. Devuelve int 0xRRGGBB. */
    public static int hsvToRgb(float h, float s, float v) {
        if (s == 0f) {
            int c = (int)(v * 255f);
            return (c << 16) | (c << 8) | c;
        }

        float hh = (h % 360f) / 60f;
        int   i  = (int) hh;
        float ff = hh - i;
        float p  = v * (1f - s);
        float q  = v * (1f - s * ff);
        float t  = v * (1f - s * (1f - ff));

        float r, g, b;
        switch (i) {
            case 0  -> { r = v; g = t; b = p; }
            case 1  -> { r = q; g = v; b = p; }
            case 2  -> { r = p; g = v; b = t; }
            case 3  -> { r = p; g = q; b = v; }
            case 4  -> { r = t; g = p; b = v; }
            default -> { r = v; g = p; b = q; }
        }

        return (((int)(r * 255f)) << 16)
                | (((int)(g * 255f)) <<  8)
                |  ((int)(b * 255f));
    }

    private static Integer parseHex(String s) {
        if (s == null) return null;
        String t = s.trim().replaceFirst("^#", "");
        if (t.length() != 6) return null;
        try { return Integer.parseInt(t, 16) & 0xFFFFFF; }
        catch (Exception e) { return null; }
    }
}