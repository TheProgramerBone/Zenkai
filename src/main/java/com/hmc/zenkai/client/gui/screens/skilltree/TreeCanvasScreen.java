package com.hmc.zenkai.client.gui.screens.skilltree;

import com.hmc.zenkai.client.gui.screens.ZenkaiMenuScreen;
import com.hmc.zenkai.client.gui.widgets.TutorialOverlay;
import com.hmc.zenkai.util.ZenkaiNumbers;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Lienzo común de los dos skill trees ({@link SkillTreeScreen}: formas; {@link SkillsTreeScreen}: skills por maestro).
 *
 * POR QUÉ EXISTE. Las dos pantallas tenían copiados ~30 funciones idénticas (geometría del visor, conversiones
 * mundo↔pantalla, arrastre, soltar, cámara animada, placas de TP/MND, pulso, anillo…) y cada cambio —el «?» de
 * ayuda, el fundido de fondos— había que hacerlo dos veces. Aquí vive SOLO lo que era literalmente igual; lo que
 * difiere (nodos, dibujo, tooltips, tutorial, compra) sigue en cada subclase.
 *
 * ESTADO. Pan y zoom son STATIC de cada subclase (la pestaña construye una pantalla nueva en cada visita y debe
 * recordar la vista): por eso el lienzo los lee y escribe mediante {@link #panX()}, {@link #panY()},
 * {@link #setPan(float, float)} y {@link #zoomIndex()} y NO los guarda aquí, o las dos pantallas compartirían
 * cámara. Lo que sí es de instancia (arrastre en curso, destino de la cámara, tutorial) vive aquí.
 */
public abstract class TreeCanvasScreen extends ZenkaiMenuScreen {

    // ── Geometría (px de un panel de 256; el beige real va de x=12 a x=243, y de 13 a 242) ──

    protected static final int VP_LEFT = 12, VP_RIGHT = 244, VP_TOP = 13, VP_BOTTOM = 243;
    protected static final int ICON_BTN = 20, BTN_PLATE = 24;
    protected static final float[] ZOOMS = {0.5f, 1f, 2f};
    protected static final int DEFAULT_ZOOM = 1;
    protected static final double DRAG_SLOP = 3.0;

    // ── Estado de instancia compartido ──

    protected boolean pressedInViewport, dragging;
    protected double pressX, pressY;
    protected @Nullable TutorialOverlay tutorial;
    /** Destino de la cámara (mundo) mientras el tutorial la lleva a un nodo; null = quieta. */
    protected float @Nullable [] camGoal;

    protected TreeCanvasScreen(Component title) {
        super(title);
    }

    // ── Lo que cada árbol pone ──

    /** Pan actual (X de mundo en el centro del visor). Vive en un static de la subclase. */
    protected abstract float panX();
    protected abstract float panY();
    protected abstract void setPan(float x, float y);
    /** Índice del zoom actual dentro de {@link #ZOOMS}. */
    protected abstract int zoomIndex();
    /** Limita el pan al contenido del árbol. */
    protected abstract void clampPan();
    /** Se soltó el botón sin arrastrar: clic en lo que haya en (mx, my) (un nodo o nada). */
    protected abstract void onCanvasClick(double mx, double my);

    // ── Geometría del visor ──

    protected int px(int v) { return panelLeft + v * BG_W / 256; }
    protected int py(int v) { return panelTop + v * BG_H / 256; }
    protected int vpLeft()   { return px(VP_LEFT); }
    protected int vpRight()  { return px(VP_RIGHT); }
    protected int vpTop()    { return py(VP_TOP); }
    protected int vpBottom() { return py(VP_BOTTOM); }
    protected int btnY()     { return vpTop() + 4; }
    protected int zoomBtnX() { return vpRight() - 4 - BTN_PLATE + (BTN_PLATE - ICON_BTN) / 2; }
    protected float zoom()   { return ZOOMS[zoomIndex()]; }
    protected float vpCx()   { return (vpLeft() + vpRight()) / 2f; }
    protected float vpCy()   { return (vpTop() + vpBottom()) / 2f; }
    /** Origen del mundo en pantalla, REDONDEADO (un pan fraccionario reparte mal los texeles del arte). */
    protected float originX() { return Math.round(vpCx() - panX() * zoom()); }
    protected float originY() { return Math.round(vpCy() - panY() * zoom()); }
    protected float toScreenX(float wx) { return originX() + wx * zoom(); }
    protected float toScreenY(float wy) { return originY() + wy * zoom(); }
    protected float toWorldX(double sx) { return (float) ((sx - originX()) / zoom()); }
    protected float toWorldY(double sy) { return (float) ((sy - originY()) / zoom()); }

    protected boolean inViewport(double x, double y) {
        return x >= vpLeft() && x < vpRight() && y >= vpTop() && y < vpBottom();
    }

    protected boolean isOverWidget(double mx, double my) {
        for (var c : children()) if (c.isMouseOver(mx, my)) return true;
        return false;
    }

    // ── Entrada ──

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (tutorial != null && tutorial.keyPressed(key)) return true;
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (pressedInViewport && button == 0) {
            if (!dragging && Math.hypot(mx - pressX, my - pressY) > DRAG_SLOP) dragging = true;
            if (dragging) {
                camGoal = null;
                setPan(panX() - (float) (dx / zoom()), panY() - (float) (dy / zoom()));
                clampPan();
            }
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (pressedInViewport && button == 0) {
            pressedInViewport = false;
            if (!dragging) onCanvasClick(mx, my);
            dragging = false;
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    /** Avanza la cámara hacia su destino (fracción por frame, suave) y la suelta al llegar. */
    protected void stepCamera() {
        if (camGoal == null) return;
        float dx = camGoal[0] - panX(), dy = camGoal[1] - panY();
        if (Math.abs(dx) < 0.6f && Math.abs(dy) < 0.6f) {
            setPan(camGoal[0], camGoal[1]);
            camGoal = null;
        } else {
            setPan(panX() + dx * 0.22f, panY() + dy * 0.22f);
        }
        clampPan();
    }

    // ── Placas de TP/MND y utilidades de dibujo ──

    protected int plateWidth(Component text) { return 5 + this.font.width(text) + 5; }

    protected Component tpText() {
        return Component.translatable("screen.zenkai.technique.tp", ZenkaiNumbers.format(att == null ? 0 : att.getTP()));
    }

    protected static int pulse(int rgb, long periodMs) {
        double t = (Util.getMillis() % periodMs) / (double) periodMs;
        int a = (int) (110 + 145 * (0.5 + 0.5 * Math.sin(t * Math.PI * 2)));
        return (a << 24) | (rgb & 0xFFFFFF);
    }

    protected static int pulseAlpha(long periodMs, int min, int max) {
        double t = (Util.getMillis() % periodMs) / (double) periodMs;
        return (int) (min + (max - min) * (0.5 + 0.5 * Math.sin(t * Math.PI * 2)));
    }

    protected static void ring(GuiGraphics g, int x, int y, int size, int color) {
        g.fill(x, y, x + size, y + 1, color);
        g.fill(x, y + size - 1, x + size, y + size, color);
        g.fill(x, y + 1, x + 1, y + size - 1, color);
        g.fill(x + size - 1, y + 1, x + size, y + size - 1, color);
    }

    protected static int darken(int argb, float f) {
        int r = (int) (((argb >> 16) & 0xFF) * f), gr = (int) (((argb >> 8) & 0xFF) * f), b = (int) ((argb & 0xFF) * f);
        return (r << 16) | (gr << 8) | b;
    }

    /** Línea de tooltip: texto y color. */
    protected record TipLine(Component text, int color) {}
}
