package com.hmc.zenkai.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * Dibuja una textura ornamentada a un tamaño ARBITRARIO conservando las esquinas.
 * Existe por btn_wide.png: mide 60x25 y tiene esquinas recortadas con brillo dorado. Hasta
 * ahora TextOnlyButton la blitteaba con getWidth()/getHeight() como tamaño de textura, así que
 * un botón de 120x16 la estiraba entera y las esquinas salían deformadas — por eso la mayoría
 * de botones acabaron SIN fondo, lo que a su vez dejó media interfaz sin ningún indicio de que
 * algo fuera clicable.
 * Con 9-slice el mismo asset sirve para el botón de 46x16 de "Asignar" y para el de 170x20 de
 * un deseo, sin pedirte un PNG por tamaño.
 * ⚠ API: usa el overload de GuiGraphics#blit de 11 argumentos (x, y, w, h, u, v, uW, vH, texW,
 * texH), que es el que ESCALA. El de 9 argumentos recorta 1:1 y aquí no sirve.
 */
public final class NineSlice {
    private NineSlice() {}

    /** Esquina de btn_wide.png: 12 px es donde termina el ornamento y empieza el borde recto. */
    public static final int BTN_CORNER = 12;
    public static final int BTN_TEX_W = 60;
    public static final int BTN_TEX_H = 25;

    public static final ResourceLocation BTN_WIDE =
            ResourceLocation.fromNamespaceAndPath("zenkai", "textures/gui/btn_wide.png");

    /** btn_wide con sus medidas ya puestas. Lo que llama el mod. */
    public static void button(GuiGraphics g, int x, int y, int w, int h) {
        draw(g, BTN_WIDE, x, y, w, h, BTN_TEX_W, BTN_TEX_H, BTN_CORNER);
    }

    /**
     * Nueve regiones: 4 esquinas a tamaño natural, 4 bordes estirados en un eje, centro
     * estirado en los dos.
     * Si el destino es más pequeño que las dos esquinas juntas, se reparte a medias en vez de
     * dejar que el borde se solape con la esquina opuesta y salga un churro: un botón de 16 px
     * de alto con esquinas de 12 es un caso REAL (los ✖ de las listas), no un extremo teórico.
     */
    public static void draw(GuiGraphics g, ResourceLocation tex, int x, int y, int w, int h,
                            int texW, int texH, int corner) {
        if (w <= 0 || h <= 0) return;

        int cw = Math.min(corner, w / 2);
        int ch = Math.min(corner, h / 2);
        int midW = w - cw * 2;              // ancho del tramo estirado horizontal
        int midH = h - ch * 2;
        int srcMidW = texW - corner * 2;    // ancho del tramo fuente
        int srcMidH = texH - corner * 2;

        // Esquinas
        blit(g, tex, x,           y,           cw, ch, 0,                  0,                  corner, corner, texW, texH);
        blit(g, tex, x + w - cw,  y,           cw, ch, texW - corner,      0,                  corner, corner, texW, texH);
        blit(g, tex, x,           y + h - ch,  cw, ch, 0,                  texH - corner,      corner, corner, texW, texH);
        blit(g, tex, x + w - cw,  y + h - ch,  cw, ch, texW - corner,      texH - corner,      corner, corner, texW, texH);

        // Bordes horizontales
        if (midW > 0) {
            blit(g, tex, x + cw, y,          midW, ch, corner, 0,             srcMidW, corner, texW, texH);
            blit(g, tex, x + cw, y + h - ch, midW, ch, corner, texH - corner, srcMidW, corner, texW, texH);
        }
        // Bordes verticales
        if (midH > 0) {
            blit(g, tex, x,          y + ch, cw, midH, 0,             corner, corner, srcMidH, texW, texH);
            blit(g, tex, x + w - cw, y + ch, cw, midH, texW - corner, corner, corner, srcMidH, texW, texH);
        }
        // Centro
        if (midW > 0 && midH > 0) {
            blit(g, tex, x + cw, y + ch, midW, midH, corner, corner, srcMidW, srcMidH, texW, texH);
        }
    }

    private static void blit(GuiGraphics g, ResourceLocation tex, int x, int y, int w, int h,
                             int u, int v, int uW, int vH, int texW, int texH) {
        g.blit(tex, x, y, w, h, (float) u, (float) v, uW, vH, texW, texH);   // ⚠ API
    }

    /** Esquina de btn_tech.png: 8 px, lo justo para que el remache no se estire. */
    public static final int BTN_TECH_CORNER = 8;

    public static final ResourceLocation BTN_TECH =
            ResourceLocation.fromNamespaceAndPath("zenkai", "textures/gui/btn_tech.png");

    /** btn_tech con sus medidas ya puestas. Familia tecnológica (TechButton). */
    public static void techButton(GuiGraphics g, int x, int y, int w, int h) {
        draw(g, BTN_TECH, x, y, w, h, BTN_TEX_W, BTN_TEX_H, BTN_TECH_CORNER);
    }
}