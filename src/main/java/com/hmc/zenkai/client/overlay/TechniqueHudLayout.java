package com.hmc.zenkai.client.overlay;

import com.hmc.zenkai.config.ClientConfig;
import com.hmc.zenkai.feature.player.PlayerTechniques;
import net.minecraft.util.Mth;

/**
 * DÓNDE va cada celda del HUD de técnicas. Cálculo puro, sin dibujar nada.
 * Existe separado del overlay porque hay DOS consumidores que tienen que coincidir al píxel:
 * el HUD real y la pantalla de colocación, donde el jugador arrastra el bloque. Con la
 * aritmética duplicada, el fantasma que se arrastra acabaría a unos píxeles de donde luego se
 * dibuja el HUD, y esa diferencia es justo lo que el jugador está intentando ajustar.
 * EL DESPLAZAMIENTO ES EN PÍXELES DE GUI, no de pantalla: los mismos que usa GuiGraphics. Así
 * un offset de 10 significa lo mismo con GUI Scale 2 que con 4 — en píxeles reales serían 20 o
 * 40, pero el jugador ajusta mirando la interfaz, no el monitor.
 */
public final class TechniqueHudLayout {

    public static final int CELL = 20;
    public static final int GAP = 2;

    /** Alto de la hotbar vanilla más su aire. La barra vanilla mide 22 y deja 1 px abajo. */
    private static final int HOTBAR_H = 23;
    /** Media anchura de la hotbar vanilla (182 px de ancho total). */
    private static final int HOTBAR_HALF_W = 91;
    /** Alto de la barra de experiencia vanilla, que se dibuja justo encima de la hotbar y por
     *  debajo de la fila de vida. Faltaba en la reserva: el bloque se calculaba como si la vida
     *  empezara pegada a la hotbar, así que en partida (con la barra de XP siempre presente en
     *  survival, vacía o no) quedaba justo esos píxeles más bajo de lo previsto y la fila de
     *  armadura, que va encima de la vida, se lo comía. Fija por el mismo motivo que
     *  HEALTH_ARMOR_H: no depende de cuánta experiencia tenga el jugador ahora mismo. */
    private static final int EXPERIENCE_BAR_H = 10;
    /** Alto que añaden encima de la hotbar las barras vanilla de vida y armadura (Gui.leftHeight:
     *  +10 por la fila de corazones, +10 más si hay armadura puesta). Se reserva FIJO, aunque el
     *  jugador no lleve armadura en este instante: si el hueco cambiara de tamaño cada vez que se
     *  pone o quita una pieza, el bloque saltaría de sitio solo en pleno combate. */
    private static final int HEALTH_ARMOR_H = 20;

    private final int x, y, w, h;
    private final boolean horizontal;

    private TechniqueHudLayout(int x, int y, int w, int h, boolean horizontal) {
        this.x = x; this.y = y; this.w = w; this.h = h; this.horizontal = horizontal;
    }

    public int x() { return x; }
    public int y() { return y; }
    public int width() { return w; }
    public int height() { return h; }
    public boolean horizontal() { return horizontal; }

    public int cellX(int index) { return horizontal ? x + index * (CELL + GAP) : x; }
    public int cellY(int index) { return horizontal ? y : y + index * (CELL + GAP); }

    public boolean contains(double mx, double my) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    /** Alto reservado por la hotbar vanilla + su barra de XP + la fila de vida/armadura, medido
     *  desde el borde inferior de pantalla. Fijo aunque el jugador no lleve armadura ahora
     *  mismo, por el mismo motivo que HEALTH_ARMOR_H: que el bloque no salte de sitio solo por
     *  ponerse o quitarse una pieza. Público para que otros overlays (KiChargeGaugeOverlay) se
     *  apoyen encima sin duplicar los cuatro números. */
    public static int vanillaBottomReserve() {
        return HOTBAR_H + EXPERIENCE_BAR_H + HEALTH_ARMOR_H;
    }

    /** Tamaño del bloque de nueve celdas en la orientación dada. */
    public static int blockWidth(boolean horizontal) {
        int n = PlayerTechniques.BIND_POSITIONS;
        return horizontal ? n * CELL + (n - 1) * GAP : CELL;
    }

    public static int blockHeight(boolean horizontal) {
        int n = PlayerTechniques.BIND_POSITIONS;
        return horizontal ? CELL : n * CELL + (n - 1) * GAP;
    }

    /** Layout con la configuración guardada. */
    public static TechniqueHudLayout current(int screenW, int screenH) {
        return of(screenW, screenH,
                ClientConfig.hudAnchor(), ClientConfig.hudOrientation(),
                ClientConfig.hudOffsetX(), ClientConfig.hudOffsetY(),
                ClientConfig.hudAvoidHotbar());
    }

    /**
     * Layout con parámetros explícitos. La pantalla de colocación lo usa con los valores que
     * el jugador está moviendo AHORA, sin escribirlos todavía en la config.
     */
    public static TechniqueHudLayout of(int screenW, int screenH, HudAnchor anchor,
                                        HudOrientation orientation, int offX, int offY,
                                        boolean avoidHotbar) {
        boolean horizontal = orientation.isHorizontal();
        int w = blockWidth(horizontal);
        int h = blockHeight(horizontal);

        int x = anchor.originX(screenW, w) + offX;
        int y = anchor.originY(screenH, h) + offY;

        if (avoidHotbar) y = pushAboveHotbar(x, y, w, h, screenW, screenH);

        // El bloque nunca sale de la pantalla, pase lo que pase con el offset guardado: un
        // desplazamiento cómodo en 4K deja el HUD fuera de una ventana pequeña, y un HUD que no
        // se ve es indistinguible de un HUD roto.
        x = Mth.clamp(x, 0, Math.max(0, screenW - w));
        y = Mth.clamp(y, 0, Math.max(0, screenH - h));

        return new TechniqueHudLayout(x, y, w, h, horizontal);
    }

    /**
     * Sube el bloque por encima de la hotbar vanilla (y de vida/armadura, que se dibujan justo
     * encima de ella a la izquierda) si se le echaría encima.
     * Solo actúa si hay solape REAL en los dos ejes: un bloque abajo-izquierda en una pantalla
     * ancha no toca la hotbar, que está centrada y mide 182 px, y subirlo "por si acaso" lo
     * dejaría flotando sin motivo. Se comprueba contra el rect combinado (hotbar + vida +
     * armadura), no contra la mitad inferior de la pantalla.
     */
    private static int pushAboveHotbar(int x, int y, int w, int h, int screenW, int screenH) {
        int hotbarTop = screenH - vanillaBottomReserve();
        int hotbarLeft = screenW / 2 - HOTBAR_HALF_W;
        int hotbarRight = screenW / 2 + HOTBAR_HALF_W;

        boolean overlapsY = y + h > hotbarTop;
        boolean overlapsX = x < hotbarRight && x + w > hotbarLeft;
        if (!overlapsY || !overlapsX) return y;

        return hotbarTop - h - 2;
    }
}