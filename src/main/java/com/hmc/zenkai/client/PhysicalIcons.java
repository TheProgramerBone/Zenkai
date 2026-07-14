package com.hmc.zenkai.client;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.core.network.feature.race.RaceTextureUtil;
import com.hmc.zenkai.core.technique.PhysicalTechnique;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * Íconos de técnicas físicas (overlay + pestaña). Atlas physical_icons.png 80x20,
 * celdas 20x20 en orden del enum. Sin teñir (las físicas no tienen color).
 * Fallback sin atlas: cuadrito gris con la inicial.
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
        long now = System.currentTimeMillis();
        if (now >= nextCheckMs) {
            nextCheckMs = now + 2000;
            atlasExists = RaceTextureUtil.resourceExists(ATLAS);
        }
        if (!atlasExists) {
            g.fill(x + 2, y + 2, x + CELL - 2, y + CELL - 2, 0xFF808080);
            g.drawString(net.minecraft.client.Minecraft.getInstance().font,
                    t.name().substring(0, 1), x + 7, y + 6, 0xFFFFFFFF, true);
            return;
        }
        g.blit(ATLAS, x, y, t.ordinal() * CELL, 0, CELL, CELL, ATLAS_W, ATLAS_H);
    }
}