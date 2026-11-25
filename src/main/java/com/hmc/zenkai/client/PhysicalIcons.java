package com.hmc.zenkai.client;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.feature.race.RaceTextureUtil;
import com.hmc.zenkai.feature.technique.PhysicalTechnique;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * Íconos de técnicas físicas (overlay + pestaña). Un PNG de 20x20 por técnica en
 * textures/gui/technique_physical/<id>.png (id = enum en minúsculas, incluidas las de espada);
 * el campo icon del JSON, si existe, manda sobre esa ruta. Sin teñir (las físicas no tienen color).
 * Mismo añadido que TechniqueIcons: overload con tamaño, para que quepan dentro del marco de
 * SlotCell sin taparlo. Dibujados a 20x20 sobre una celda de 20x20 pisaban el borde y el número.
 */
public final class PhysicalIcons {
    private PhysicalIcons() {}

    private static final int CELL = 20;
    private static final ResourceLocation VANILLA_MOON =
            ResourceLocation.withDefaultNamespace("textures/environment/moon_phases.png");

    private static final ResourceLocation[] LOCATIONS = new ResourceLocation[PhysicalTechnique.values().length];
    private static final boolean[] exists = new boolean[LOCATIONS.length];
    private static long nextCheckMs = 0L;

    private static ResourceLocation locationOf(PhysicalTechnique t) {
        int i = t.ordinal();
        if (LOCATIONS[i] == null) {
            LOCATIONS[i] = ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID,
                    "textures/gui/technique_physical/" + t.id() + ".png");
        }
        return LOCATIONS[i];
    }

    public static void draw(GuiGraphics g, int x, int y, PhysicalTechnique t) {
        draw(g, x, y, CELL, t);
    }

    public static void draw(GuiGraphics g, int x, int y, int size, PhysicalTechnique t) {
        if (size <= 0) return;

        // Luna Falsa: usa la LUNA LLENA de vanilla (celda 0,0 de moon_phases.png, 4x2 celdas de 32x32) en vez de un PNG propio
        // (decisión del usuario, 2026-11-25). No se copia el asset de Mojang: se dibuja un recorte del suyo.
        if (t == PhysicalTechnique.FALSE_MOON) {
            RenderSystem.enableBlend();
            g.setColor(1f, 1f, 1f, 1f);
            g.blit(VANILLA_MOON, x, y, size, size, 0f, 0f, 32, 32, 128, 64);
            RenderSystem.disableBlend();
            return;
        }

        // Icono propio (TechniqueDef.icon, 0.3.0): mismo escape hatch que TechniqueIcons.
        String custom = t.icon();
        if (!custom.isEmpty()) {
            ResourceLocation id = ResourceLocation.tryParse(custom);
            if (id != null && RaceTextureUtil.resourceExists(id)) {
                RenderSystem.enableBlend();
                g.setColor(1f, 1f, 1f, 1f);
                g.blit(id, x, y, size, size, 0f, 0f, CELL, CELL, CELL, CELL);
                RenderSystem.disableBlend();
                return;
            }
        }

        long now = System.currentTimeMillis();
        if (now >= nextCheckMs) {
            nextCheckMs = now + 2000;
            for (PhysicalTechnique p : PhysicalTechnique.values()) {
                exists[p.ordinal()] = RaceTextureUtil.resourceExists(locationOf(p));
            }
        }

        if (!exists[t.ordinal()]) {
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
        g.blit(locationOf(t), x, y, size, size, 0f, 0f, CELL, CELL, CELL, CELL);
        g.setColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
    }
}