package com.hmc.zenkai.client;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.feature.race.RaceTextureUtil;
import com.hmc.zenkai.feature.technique.KiTechnique;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * Íconos de técnicas para GUI/HUD (overlay de combate, menú, barra de asignación).
 * Atlas: textures/gui/technique_icons.png (180x20, celdas 20x20):
 *  - Celdas 0-6: ícono por tipo, en orden del enum (WAVE, BLAST, LAZER, SPIRAL, BIG_BLAST,
 *    BARRIER, BURST), dibujados en BLANCO/grises -> se TIÑEN con el color de la técnica.
 *  - Celda 8: base EXPLOSIVA (se dibuja debajo del ícono, SIN teñir).
 * NUEVO: overload con tamaño. Los íconos son 20x20 y las celdas de la barra de asignación
 * también, así que dibujados a tamaño natural ocupaban la celda ENTERA y se comían el marco
 * y el número de la posición. Con el marco ahora dentro de SlotCell hace falta poder pedirlos
 * a 16x16 sin recortarlos.
 * Se hereda el estado de blend explícito que ya tenía PhysicalIcons: setColor es GLOBAL y
 * cualquier widget anterior del frame puede haber dejado un alfa < 1 sin resetear.
 */
public final class TechniqueIcons {
    private TechniqueIcons() {}

    private static final ResourceLocation ATLAS =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/technique_icons.png");
    private static final int CELL = 20;
    private static final int ATLAS_W = 180;
    private static final int ATLAS_H = 40; // ◄ Ampliado a 40px para 2 filas
    private static final int EXPLOSIVE_CELL = 8;

    private static boolean atlasExists = false;
    private static long nextCheckMs = 0L;

    public static void draw(GuiGraphics g, int x, int y, KiTechnique t) {
        draw(g, x, y, CELL, t);
    }

    public static void draw(GuiGraphics g, int x, int y, int size, KiTechnique t) {
        if (size <= 0) return;

        long now = System.currentTimeMillis();
        if (now >= nextCheckMs) {
            nextCheckMs = now + 2000;
            atlasExists = RaceTextureUtil.resourceExists(ATLAS);
        }

        if (!atlasExists) {
            int m1 = Math.max(1, size / 20);
            int m3 = Math.max(2, size * 3 / 20);
            if (t.explosive()) {
                g.fill(x + m1, y + m1, x + size - m1, y + size - m1, 0xFFFF6622);
            }
            g.fill(x + m3, y + m3, x + size - m3, y + size - m3, 0xFF000000 | t.rgb());
            return;
        }

        RenderSystem.enableBlend();

        // 0. Base explosiva (sin teñir, en Y = 0)
        if (t.explosive()) {
            blit(g, x, y, size, EXPLOSIVE_CELL * CELL, 0);
        }

        int u = t.type().ordinal() * CELL;

        // 1. CAPA BASE: Se tiñe con el color RGB de la técnica (Fila 0, Y = 0)
        int rgb = t.rgb();
        g.setColor(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f, 1f);
        blit(g, x, y, size, u, 0);

        // 2. CAPA SUPERIOR: Detalle/Brillo blanco puro SIN teñir (Fila 1, Y = CELL)
        g.setColor(1f, 1f, 1f, 1f); // Reset del tinte a blanco
        blit(g, x, y, size, u, CELL);

        RenderSystem.disableBlend();
    }

    /** Overload de blit adaptado para recibir coordenada V (fila). */
    private static void blit(GuiGraphics g, int x, int y, int size, int u, int v) {
        g.blit(ATLAS, x, y, size, size, (float) u, (float) v, CELL, CELL, ATLAS_W, ATLAS_H);
    }
}