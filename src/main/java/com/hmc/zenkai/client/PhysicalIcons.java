package com.hmc.zenkai.client;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.feature.race.RaceTextureUtil;
import com.hmc.zenkai.feature.technique.PhysicalTechnique;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * Íconos de técnicas físicas (overlay + pestaña). Atlas physical_icons.png 80x20, celdas 20x20
 * en orden del enum. Sin teñir (las físicas no tienen color).
 * Mismo añadido que TechniqueIcons: overload con tamaño, para que quepan dentro del marco de
 * SlotCell sin taparlo. Dibujados a 20x20 sobre una celda de 20x20 pisaban el borde y el número.
 */
public final class PhysicalIcons {
    private PhysicalIcons() {}

    private static final ResourceLocation ATLAS =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/physical_icons.png");
    private static final int CELL = 20;
    private static final int ATLAS_W = 80;
    private static final int ATLAS_H = 20;

    private static boolean atlasExists = false;
    private static long nextCheckMs = 0L;

    public static void draw(GuiGraphics g, int x, int y, PhysicalTechnique t) {
        draw(g, x, y, CELL, t);
    }

    public static void draw(GuiGraphics g, int x, int y, int size, PhysicalTechnique t) {
        if (size <= 0) return;

        long now = System.currentTimeMillis();
        if (now >= nextCheckMs) {
            nextCheckMs = now + 2000;
            atlasExists = RaceTextureUtil.resourceExists(ATLAS);
        }

        if (!atlasExists) {
            int m = Math.max(1, size / 10);
            g.fill(x + m, y + m, x + size - m, y + size - m, 0xFF808080);
            g.drawString(Minecraft.getInstance().font, t.name().substring(0, 1),
                    x + size / 2 - 2, y + size / 2 - 4, 0xFFFFFFFF, true);
            return;
        }

        // Estado explícito: setColor es GLOBAL y cualquier widget dibujado antes en el frame
        // puede haber dejado un alfa < 1 sin resetear. Sin esto el ícono hereda esa
        // transparencia y aparece medio invisible según qué pantalla esté abierta.
        RenderSystem.enableBlend();
        g.setColor(1f, 1f, 1f, 1f);
        g.blit(ATLAS, x, y, size, size, (float) (t.ordinal() * CELL), 0f,
                CELL, CELL, ATLAS_W, ATLAS_H);   // ⚠ API: overload que escala
        g.setColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
    }
}