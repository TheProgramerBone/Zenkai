package com.hmc.zenkai.client.gui.widgets;

import com.hmc.zenkai.client.gui.ZenkaiPalette;
import com.hmc.zenkai.config.ClientConfig;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.function.Supplier;

/**
 * Mini tutorial de una pantalla (skill tree F7, reutilizado por Training — reporte #3): una serie
 * de pasos que oscurecen la pantalla salvo un rectángulo resaltado y explican qué hay ahí. Sale
 * sola UNA vez por instalación (ClientConfig.tutorialSeen) y el botón «?» de la pantalla la
 * repite con {@link #start}.
 *
 * Uso desde una pantalla: crearla con su id y sus pasos; llamar a {@link #startIfFirstTime} en
 * init, a {@link #render} al FINAL de render() (por encima del resto), y delegarle primero
 * mouseClicked/keyPressed: mientras está activa se come cualquier clic (avanza) y Esc la cierra.
 * Los rectángulos son proveedores porque el panel se recoloca al cambiar el tamaño de la ventana.
 */
public final class TutorialOverlay {

    /**
     * Un paso: título, texto y la zona que se resalta ({x, y, w, h} en px de GUI, o null = ninguna).
     * {@code onEnter} (opcional) se ejecuta al LLEGAR al paso: las pantallas con lienzo desplazable lo
     * usan para llevar la cámara al elemento resaltado (si no, el recuadro caía sobre lo que hubiera
     * donde el nodo debería estar, o fuera del visor). El rect se vuelve a pedir en cada frame, así
     * que sigue al elemento mientras la cámara se mueve.
     */
    public record Step(Component title, Component body, Supplier<int @Nullable []> rect,
                       @Nullable Runnable onEnter) {
        public Step(Component title, Component body, Supplier<int @Nullable []> rect) {
            this(title, body, rect, null);
        }
    }

    private static final int DIM = 0xB0000000;
    private static final int BOX_BG = 0xF0100C1C;
    private static final int BOX_BORDER = 0xFFE8901C;
    private static final int BOX_W = 200;

    private final String id;
    private final List<Step> steps;
    private int index = -1;

    public TutorialOverlay(String id, List<Step> steps) {
        this.id = id;
        this.steps = steps;
    }

    public boolean active() { return index >= 0 && index < steps.size(); }

    public void start() {
        index = steps.isEmpty() ? -1 : 0;
        enter();
    }

    private void enter() {
        if (!active()) return;
        Runnable r = steps.get(index).onEnter();
        if (r != null) r.run();
    }

    public void startIfFirstTime() {
        if (!ClientConfig.tutorialSeen(id)) start();
    }

    private void finish() {
        index = -1;
        ClientConfig.markTutorialSeen(id);
    }

    /** Clic: siguiente paso (el último cierra). Devuelve true si lo consumió. */
    public boolean mouseClicked() {
        if (!active()) return false;
        index++;
        if (index >= steps.size()) finish();
        else enter();
        return true;
    }

    /** Esc salta el tutorial; Intro/espacio avanzan. Devuelve true si lo consumió. */
    public boolean keyPressed(int key) {
        if (!active()) return false;
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            finish();
            return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_SPACE || key == GLFW.GLFW_KEY_RIGHT) {
            return mouseClicked();
        }
        return true; // mientras está abierto, ninguna tecla llega a la pantalla
    }

    public void render(GuiGraphics g, Font font, int screenW, int screenH) {
        if (!active()) return;
        Step step = steps.get(index);
        int[] r = step.rect().get();
        var pose = g.pose();
        pose.pushPose();
        pose.translate(0, 0, 500);

        // Velo con hueco: cuatro bandas alrededor del rectángulo resaltado.
        if (r == null) {
            g.fill(0, 0, screenW, screenH, DIM);
        } else {
            int x1 = r[0], y1 = r[1], x2 = r[0] + r[2], y2 = r[1] + r[3];
            g.fill(0, 0, screenW, y1, DIM);
            g.fill(0, y2, screenW, screenH, DIM);
            g.fill(0, y1, x1, y2, DIM);
            g.fill(x2, y1, screenW, y2, DIM);
            double t = (Util.getMillis() % 1200L) / 1200.0;
            int a = (int) (120 + 135 * (0.5 + 0.5 * Math.sin(t * Math.PI * 2)));
            int c = (a << 24) | (ZenkaiPalette.GOLD & 0xFFFFFF);
            g.fill(x1 - 1, y1 - 1, x2 + 1, y1, c);
            g.fill(x1 - 1, y2, x2 + 1, y2 + 1, c);
            g.fill(x1 - 1, y1, x1, y2, c);
            g.fill(x2, y1, x2 + 1, y2, c);
        }

        // Caja de texto: debajo de la zona si cabe, si no encima; centrada si no hay zona.
        List<FormattedCharSequence> body = font.split(step.body(), BOX_W - 10);
        Component footer = Component.translatable("screen.zenkai.tutorial.footer", index + 1, steps.size());
        int h = 6 + 11 + body.size() * 10 + 4 + 10 + 4;
        int x, y;
        if (r == null) {
            x = (screenW - BOX_W) / 2;
            y = (screenH - h) / 2;
        } else {
            x = Math.max(4, Math.min(screenW - BOX_W - 4, r[0] + r[2] / 2 - BOX_W / 2));
            y = r[1] + r[3] + 6;
            if (y + h > screenH - 4) y = r[1] - h - 6;
            y = Math.max(4, Math.min(screenH - h - 4, y));
        }
        g.fill(x - 1, y - 1, x + BOX_W + 1, y + h + 1, 0xFF1A0C04);
        g.fill(x, y, x + BOX_W, y + h, BOX_BORDER);
        g.fill(x + 1, y + 1, x + BOX_W - 1, y + h - 1, BOX_BG);
        int ty = y + 5;
        g.drawString(font, step.title(), x + 5, ty, 0xFFFFE070, true);
        ty += 11;
        for (FormattedCharSequence line : body) {
            g.drawString(font, line, x + 5, ty, 0xFFE0DCEC, true);
            ty += 10;
        }
        ty += 4;
        g.drawString(font, footer, x + 5, ty, 0xFFA8A4B8, true);
        pose.popPose();
    }
}
