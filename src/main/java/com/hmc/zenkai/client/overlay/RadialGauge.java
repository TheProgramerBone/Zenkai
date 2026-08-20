package com.hmc.zenkai.client.overlay;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/**
 * Anillo de progreso circular reutilizable, con el MISMO lenguaje visual que los sectores de
 * WheelScreen (que delega en {@link #arc} para no mantener dos copias del Tesselator): contorno
 * negro por detrás, degradado radial (oscuro por dentro, claro por fuera) en vez de color plano,
 * y un filo brillante en el borde exterior del tramo relleno. Sin esto un anillo de un solo
 * color liso se lee como una pegatina pegada encima del mundo en vez de una pieza de HUD con
 * volumen — que era exactamente la queja.
 *
 * Lo usan KiChargeGaugeOverlay (% de poder al cargar ki) y TransformGaugeOverlay (hold de
 * transformación): mismo dibujo, dos contextos.
 */
public final class RadialGauge {
    private RadialGauge() {}

    private static final int SEGMENTS_FULL = 40;

    /** Base oscura sobre la que se tiñe cada tono, igual que WheelScreen.COL_BACKDROP: sin
     *  mezclar contra algo oscuro el color puro se ve plano y demasiado saturado. */
    private static final int COL_BACKDROP = 0x101014;
    private static final int COL_OUTLINE  = 0xE6000000;

    /** Cuánto se acerca el tono al color puro por dentro (55%) y por fuera (100%): el mismo
     *  reparto que usa el Wheel en su relleno. */
    private static final float TINT_IN  = 0.55f;
    private static final float TINT_OUT = 1.0f;

    /**
     * Cascarón (fondo, círculo completo con bisel) + relleno proporcional a {@code progress}
     * (0..1), en sentido horario desde arriba, con degradado y filo brillante en el relleno.
     *
     * @param cx, cy   centro del anillo, en coordenadas de pantalla (píxeles).
     * @param rIn      radio interior (agujero del cascarón).
     * @param rOut     radio exterior.
     * @param bgTone   color RGB de 24 bits del cascarón vacío (sin alfa).
     * @param fillTone color RGB de 24 bits del relleno (sin alfa).
     */
    public static void ring(GuiGraphics g, float cx, float cy, float rIn, float rOut,
                            float progress, int bgTone, int fillTone) {
        float p = Mth.clamp(progress, 0f, 1f);

        // Contorno general PRIMERO y para las dos radios a la vez: un solo trazo negro por
        // detrás del cascarón, no uno distinto por tramo (se vería doblado donde el
        // relleno corta al fondo).
        outline(g, cx, cy, rIn, rOut);

        toneArc(g, cx, cy, rIn, rOut, -90f, 360f, bgTone, 0xB0);

        if (p > 0f) {
            float sweep = 360f * p;
            toneArc(g, cx, cy, rIn, rOut, -90f, sweep, fillTone, 0xF2);
            rim(g, cx, cy, rOut, -90f, sweep, fillTone);
        }
    }

    /** Contorno negro, el mismo hueco un poco más grande, por debajo de lo demás. */
    private static void outline(GuiGraphics g, float cx, float cy, float rIn, float rOut) {
        arc(g, cx, cy, rIn - 1.2f, rOut + 1.2f, -90f, 360f, COL_OUTLINE, COL_OUTLINE);
    }

    /** Tramo con degradado: oscuro (mezclado con el fondo) por dentro, tono pleno por fuera. */
    private static void toneArc(GuiGraphics g, float cx, float cy, float rIn, float rOut,
                                float startDeg, float sweepDeg, int tone, int alpha) {
        int inner = withAlpha(mix(COL_BACKDROP, tone, TINT_IN), alpha);
        int outer = withAlpha(mix(COL_BACKDROP, tone, TINT_OUT), alpha);
        arc(g, cx, cy, rIn, rOut, startDeg, sweepDeg, inner, outer);
    }

    /** Filo brillante pegado al borde exterior del relleno: banda fina en el tono a tope de
     *  brillo, la misma receta que el borde del sector activo en WheelScreen. */
    private static void rim(GuiGraphics g, float cx, float cy, float rOut,
                            float startDeg, float sweepDeg, int tone) {
        float rimIn = rOut - Math.min(2f, (rOut) * 0.2f);
        arc(g, cx, cy, rimIn, rOut, startDeg, sweepDeg, withAlpha(tone, 0xFF), 0xFFFFFFFF);
    }

    // ── Primitivos compartidos (también los usa WheelScreen) ────────────────────

    /** Interpola dos RGB de 24 bits (ignora el alfa de entrada). t=0 devuelve 'a', t=1 'b'. */
    public static int mix(int a, int b, float t) {
        int r = Mth.lerpInt(t, (a >> 16) & 0xFF, (b >> 16) & 0xFF);
        int g = Mth.lerpInt(t, (a >> 8) & 0xFF, (b >> 8) & 0xFF);
        int bl = Mth.lerpInt(t, a & 0xFF, b & 0xFF);
        return (r << 16) | (g << 8) | bl;
    }

    public static int withAlpha(int rgb, int alpha) {
        return (alpha << 24) | (rgb & 0x00FFFFFF);
    }

    /**
     * Sector anular relleno, de startDeg a startDeg+sweepDeg (grados, sentido horario), con
     * degradado entre argbIn (borde interior) y argbOut (borde exterior). Primitivo compartido
     * con WheelScreen: un anillo hecho de rectángulos se ve escalonado, así que hace falta
     * Tesselator y no GuiGraphics.fill.
     * ⚠ API de BufferBuilder/Tesselator: es la que más cambia entre versiones.
     */
    public static void arc(GuiGraphics g, float cx, float cy, float rIn, float rOut,
                           float startDeg, float sweepDeg, int argbIn, int argbOut) {
        if (sweepDeg <= 0f) return;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader); // ⚠

        Matrix4f m = g.pose().last().pose();
        BufferBuilder bb = Tesselator.getInstance()
                .begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR); // ⚠

        int segs = Math.max(1, Math.round(SEGMENTS_FULL * Math.min(1f, sweepDeg / 360f)));
        for (int i = 0; i <= segs; i++) {
            float rad = (float) Math.toRadians(startDeg + sweepDeg * ((float) i / segs));
            float cos = Mth.cos(rad), sin = Mth.sin(rad);
            bb.addVertex(m, cx + cos * rIn, cy + sin * rIn, 0f).setColor(argbIn);
            bb.addVertex(m, cx + cos * rOut, cy + sin * rOut, 0f).setColor(argbOut);
        }

        BufferUploader.drawWithShader(bb.buildOrThrow()); // ⚠
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }
}
