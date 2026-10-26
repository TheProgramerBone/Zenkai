package com.hmc.zenkai.client.gui.screens.skilltree;

import com.hmc.zenkai.Zenkai;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * Icono de una forma tal como lo pinta el árbol de transformaciones, para quien no está en esa pantalla (la HUD).
 * Lee el MISMO atlas ({@code node_icons.png}) y la MISMA lista de celdas ({@code SkillTreeArt.ICON_ORDER}): si se
 * cambia el arte de una celda, o se le añade celda a una forma, la HUD lo recoge sola, sin tocar nada más. Una forma
 * sin celda propia se dibuja con el icono genérico «?» teñido con su color de aura.
 */
public final class FormIcons {
    private FormIcons() {}

    /**
     * Dibuja el icono de {@code form} en un cuadrado de {@code size} px de GUI con la esquina en (x, y).
     * @param fallbackTint color RGB del icono genérico si la forma no tiene celda (se ignora si la tiene); -1 = blanco
     */
    public static void draw(GuiGraphics g, @Nullable ResourceLocation form, int x, int y, int size, int fallbackTint) {
        int index = SkillTreeArt.iconIndexOf(form);
        boolean generic = index == SkillTreeArt.UNKNOWN_ICON;
        int u = (index % 8) * SkillTreeArt.ICON_TEX, v = (index / 8) * SkillTreeArt.ICON_TEX;
        RenderSystem.enableBlend();
        if (generic && fallbackTint >= 0) {
            g.setColor(((fallbackTint >> 16) & 0xFF) / 255f, ((fallbackTint >> 8) & 0xFF) / 255f,
                    (fallbackTint & 0xFF) / 255f, 1f);
        }
        g.blit(SkillTreeArt.ICONS, x, y, size, size, u, v, SkillTreeArt.ICON_TEX, SkillTreeArt.ICON_TEX,
                SkillTreeArt.ICONS_SIZE, SkillTreeArt.ICONS_SIZE);
        g.setColor(1f, 1f, 1f, 1f);
    }
}
