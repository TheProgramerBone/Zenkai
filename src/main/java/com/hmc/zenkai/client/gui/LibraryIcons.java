package com.hmc.zenkai.client.gui;

import com.hmc.zenkai.Zenkai;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * Íconos de la biblioteca de apariencia en icons.png (fila v=100, tools/gen_library_icons.py),
 * pintados a cualquier tamaño (la celda es de 20 px; la estrella va a ~9 px en la esquina de una
 * casilla).
 */
public enum LibraryIcons {
    STAR(60, 100), FOLDER(80, 100), PLUS(100, 100);

    private static final ResourceLocation ATLAS =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/icons.png");
    private static final int CELL = 20;

    private final int u, v;

    LibraryIcons(int u, int v) {
        this.u = u;
        this.v = v;
    }

    public void draw(GuiGraphics g, int x, int y, int size) {
        g.blit(ATLAS, x, y, size, size, u, v, CELL, CELL, 256, 256);
    }

    /** Estrella de favorito en la esquina superior derecha de una casilla. */
    public static void favoriteBadge(GuiGraphics g, int tileX, int tileY, int tileSize) {
        int s = 10;
        // Por encima del render 3D de la miniatura (que escribe profundidad).
        g.pose().pushPose();
        g.pose().translate(0, 0, 200);
        STAR.draw(g, tileX + tileSize - s, tileY, s);
        g.pose().popPose();
    }
}
