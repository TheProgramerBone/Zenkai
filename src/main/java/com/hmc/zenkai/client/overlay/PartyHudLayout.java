package com.hmc.zenkai.client.overlay;

import com.hmc.zenkai.config.ClientConfig;
import net.minecraft.util.Mth;

/**
 * DÓNDE va el panel de party. Cálculo puro, sin dibujar: lo usan el HUD real (PartyHudOverlay) y la
 * pantalla de colocación (HudPlacementScreen), que tienen que coincidir al píxel — misma razón y
 * mismo criterio que TechniqueHudLayout (desplazamiento en píxeles de GUI, ancla elegida por
 * dónde se suelta).
 */
public final class PartyHudLayout {

    /** Una fila: diamante, cara y las tres barras. */
    public static final int DIAMOND = 7;
    public static final int FACE = 16;
    public static final int BAR_W = 50;
    public static final int BAR_H = 4;
    public static final int BAR_GAP = 1;
    public static final int ROW_H = 18;
    public static final int ROW_GAP = 3;
    public static final int PAD = 2;

    public static final int ROW_W = DIAMOND + 2 + FACE + 3 + BAR_W;

    private final int x, y, w, h, rows;

    private PartyHudLayout(int x, int y, int w, int h, int rows) {
        this.x = x; this.y = y; this.w = w; this.h = h; this.rows = rows;
    }

    public int x() { return x; }
    public int y() { return y; }
    public int width() { return w; }
    public int height() { return h; }
    public int rows() { return rows; }

    /** Y de la fila {@code i}. */
    public int rowY(int i) { return y + PAD + i * (ROW_H + ROW_GAP); }
    public int rowX() { return x + PAD; }

    public boolean contains(double mx, double my) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    public boolean overlaps(int ox, int oy, int ow, int oh) {
        return x < ox + ow && x + w > ox && y < oy + oh && y + h > oy;
    }

    public static int blockWidth() { return ROW_W + PAD * 2; }

    public static int blockHeight(int rows) {
        return rows <= 0 ? 0 : rows * ROW_H + (rows - 1) * ROW_GAP + PAD * 2;
    }

    /** Layout con la configuración guardada. */
    public static PartyHudLayout current(int screenW, int screenH, int rows) {
        return of(screenW, screenH, ClientConfig.partyHudAnchor(),
                ClientConfig.partyHudOffsetX(), ClientConfig.partyHudOffsetY(), rows);
    }

    /** Layout con parámetros explícitos (la pantalla de colocación, sin guardar todavía). */
    public static PartyHudLayout of(int screenW, int screenH, HudAnchor anchor,
                                    int offX, int offY, int rows) {
        int w = blockWidth();
        int h = blockHeight(rows);
        int x = anchor.originX(screenW, w) + offX;
        int y = anchor.originY(screenH, h) + offY;
        // Nunca fuera de la pantalla, aunque el desplazamiento guardado sea de una ventana mayor.
        x = Mth.clamp(x, 0, Math.max(0, screenW - w));
        y = Mth.clamp(y, 0, Math.max(0, screenH - h));
        return new PartyHudLayout(x, y, w, h, rows);
    }
}
