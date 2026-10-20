package com.hmc.zenkai.client.overlay;

import com.hmc.zenkai.config.ClientConfig;
import net.minecraft.util.Mth;

/**
 * DÓNDE va el panel de lectura del Ki Sense (nivel 5). Cálculo puro, igual que PartyHudLayout y
 * TechniqueHudLayout: lo usan el panel real (TargetReadingOverlay) y la pantalla de colocación
 * (HudPlacementScreen), que tienen que coincidir al píxel.
 * El alto real del panel cambia con los estados activos; la pantalla de colocación usa un alto de
 * muestra (el peor caso razonable) para que lo que se ve ahí cubra lo que pueda ocupar en juego.
 */
public final class ReadingHudLayout {
    private final int x, y, w, h;

    private ReadingHudLayout(int x, int y, int w, int h) { this.x = x; this.y = y; this.w = w; this.h = h; }

    public int x() { return x; }
    public int y() { return y; }
    public int width() { return w; }
    public int height() { return h; }

    public boolean contains(double mx, double my) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    public boolean overlaps(int ox, int oy, int ow, int oh) {
        return x < ox + ow && x + w > ox && y < oy + oh && y + h > oy;
    }

    /** Layout con la configuración guardada y el alto real del panel. */
    public static ReadingHudLayout current(int screenW, int screenH, int blockH) {
        return of(screenW, screenH, ClientConfig.readingHudAnchor(),
                ClientConfig.readingHudOffsetX(), ClientConfig.readingHudOffsetY(), blockH);
    }

    /** Layout con parámetros explícitos (la pantalla de colocación, sin guardar todavía). */
    public static ReadingHudLayout of(int screenW, int screenH, HudAnchor anchor,
                                      int offX, int offY, int blockH) {
        int w = TargetReadingOverlay.WIDTH;
        int x = anchor.originX(screenW, w) + offX;
        int y = anchor.originY(screenH, blockH) + offY;
        x = Mth.clamp(x, 0, Math.max(0, screenW - w));
        y = Mth.clamp(y, 0, Math.max(0, screenH - blockH));
        return new ReadingHudLayout(x, y, w, blockH);
    }
}
