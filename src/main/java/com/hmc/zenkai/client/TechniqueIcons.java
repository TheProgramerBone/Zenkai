package com.hmc.zenkai.client;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.core.network.feature.race.RaceTextureUtil;
import com.hmc.zenkai.core.technique.KiTechnique;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * Íconos de técnicas para GUI/HUD (overlay de combate, menú, barra de asignación).
 *
 * Atlas: textures/gui/technique_icons.png (160x20, celdas 20x20):
 *  - Celdas 0-6: ícono por tipo, en orden del enum (WAVE, BLAST, LAZER, SPIRAL,
 *    BIG_BLAST, BARRIER, BURST), dibujados en BLANCO/grises -> se TIÑEN con el color
 *    de la técnica.
 *  - Celda 7: base EXPLOSIVA (se dibuja debajo del ícono, SIN teñir).
 *
 * Composición (pedida por Juan): [base explosiva si aplica] + [ícono del tipo teñido].
 * Fallback: sin el atlas, dibuja el cuadrito de color de siempre (nada se rompe).
 */
public final class TechniqueIcons {
    private TechniqueIcons() {}

    private static final ResourceLocation ATLAS =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/technique_icons.png");
    private static final int CELL = 20;
    private static final int ATLAS_W = 180;
    private static final int ATLAS_H = 20;
    private static final int EXPLOSIVE_CELL = 8;

    // Caché de existencia (recheck periódico: recoge F3+T).
    private static boolean atlasExists = false;
    private static long nextCheckMs = 0L;

    /** Dibuja el ícono de la técnica en (x, y), tamaño 20x20. */
    public static void draw(GuiGraphics g, int x, int y, KiTechnique t) {
        long now = System.currentTimeMillis();
        if (now >= nextCheckMs) {
            nextCheckMs = now + 2000;
            atlasExists = RaceTextureUtil.resourceExists(ATLAS);
        }

        if (!atlasExists) {
            // Fallback: cuadrito de color (con marquito si es explosiva).
            if (t.explosive()) {
                g.fill(x + 1, y + 1, x + CELL - 1, y + CELL - 1, 0xFFFF6622);
            }
            g.fill(x + 3, y + 3, x + CELL - 3, y + CELL - 3, 0xFF000000 | t.rgb());
            return;
        }

        // Base explosiva (sin teñir).
        if (t.explosive()) {
            g.blit(ATLAS, x, y, EXPLOSIVE_CELL * CELL, 0, CELL, CELL, ATLAS_W, ATLAS_H);
        }

        // Ícono del tipo, teñido con el color de la técnica.
        int rgb = t.rgb();
        g.setColor(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f,
                (rgb & 0xFF) / 255f, 1f); // ⚠ GuiGraphics.setColor (1.21.1)
        g.blit(ATLAS, x, y, t.type().ordinal() * CELL, 0, CELL, CELL, ATLAS_W, ATLAS_H);
        g.setColor(1f, 1f, 1f, 1f);
    }
}