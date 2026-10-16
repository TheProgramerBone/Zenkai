package com.hmc.zenkai.client;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.feature.race.RaceTextureUtil;
import com.hmc.zenkai.feature.technique.KiTechnique;
import com.hmc.zenkai.feature.technique.KiTechniqueType;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * Íconos de técnicas para GUI/HUD (overlay de combate, menú, barra de asignación).
 * Un PNG por tipo: textures/gui/technique_ki/<id>.png (20x40, id = KiTechniqueType en minúsculas):
 *  - Fila 0 (y=0): capa BASE en BLANCO/grises -> se TIÑE con el color de la técnica.
 *  - Fila 1 (y=20): capa de DETALLE blanca, SIN teñir.
 * Los PNG salen de tools/atlas_src/technique_icons.png con tools/split_technique_icons.py; ya NO
 * hay atlas en runtime ni ordinal() que mantener alineado: un tipo nuevo = un PNG nuevo.
 * Overload con tamaño: los íconos son 20x20 y las celdas de la barra de asignación también, así
 * que a tamaño natural ocupaban la celda ENTERA y se comían el marco y el número de la posición.
 * Se hereda el estado de blend explícito que ya tenía PhysicalIcons: setColor es GLOBAL y
 * cualquier widget anterior del frame puede haber dejado un alfa < 1 sin resetear.
 */
public final class TechniqueIcons {
    private TechniqueIcons() {}

    private static final int CELL = 20;
    private static final int TEX_W = 20;
    private static final int TEX_H = 40; // 2 filas: base teñible + detalle blanco

    private static final ResourceLocation[] LOCATIONS = new ResourceLocation[KiTechniqueType.values().length];
    private static final boolean[] exists = new boolean[LOCATIONS.length];
    private static long nextCheckMs = 0L;

    private static ResourceLocation locationOf(KiTechniqueType type) {
        int i = type.ordinal();
        if (LOCATIONS[i] == null) {
            LOCATIONS[i] = ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID,
                    "textures/gui/technique_ki/" + type.id() + ".png");
        }
        return LOCATIONS[i];
    }

    /** Existencia cacheada y refrescada cada 2 s (resourceExists toca el ResourceManager). */
    private static boolean existsFor(KiTechniqueType type) {
        long now = System.currentTimeMillis();
        if (now >= nextCheckMs) {
            nextCheckMs = now + 2000;
            for (KiTechniqueType t : KiTechniqueType.values()) {
                exists[t.ordinal()] = RaceTextureUtil.resourceExists(locationOf(t));
            }
        }
        return exists[type.ordinal()];
    }

    /** Color del icono: el de la técnica para el jugador LOCAL (la Genki Dama de un malvado es otra). */
    private static int rgbOf(KiTechnique t) {
        var p = net.minecraft.client.Minecraft.getInstance().player;
        return p == null ? t.rgb() : com.hmc.zenkai.feature.technique.EvilSpiritBomb.rgb(p, t);
    }

    public static void draw(GuiGraphics g, int x, int y, KiTechnique t) {
        draw(g, x, y, CELL, t);
    }

    public static void draw(GuiGraphics g, int x, int y, int size, KiTechnique t) {
        if (size <= 0) return;

        // Icono propio (TechniqueDef.icon, 0.3.0): sustituye la celda del atlas compartido por
        // completo, incluido el marco de efecto — una técnica con textura propia dibuja su
        // propia identidad entera, no se mezcla con el lenguaje visual del atlas base.
        String custom = t.type().icon();
        if (!custom.isEmpty() && drawCustom(g, x, y, size, custom, rgbOf(t))) return;

        if (!existsFor(t.type())) {
            int m1 = Math.max(1, size / 20);
            int m3 = Math.max(2, size * 3 / 20);
            if (t.effect().borderRgb() != 0) {
                g.fill(x, y, x + size, y + size, t.effect().borderRgb());
            }
            g.fill(x + m3, y + m3, x + size - m3, y + size - m3, 0xFF000000 | rgbOf(t));
            return;
        }

        RenderSystem.enableBlend();

        // El efecto ya NO se dibuja debajo del icono: la celda 8 la ocupa ahora el tipo
        // EXPLOSION. Va como marco de 1 px alrededor, que además funciona igual en el HUD, en
        // el menú y en la barra de asignación sin robarle sitio al icono.
        int border = t.effect().borderRgb();
        if (border != 0) {
            int w = Math.max(1, size / 20);
            g.fill(x, y, x + size, y + w, border);
            g.fill(x, y + size - w, x + size, y + size, border);
            g.fill(x, y + w, x + w, y + size - w, border);
            g.fill(x + size - w, y + w, x + size, y + size - w, border);
        }

        // 1. CAPA BASE: Se tiñe con el color RGB de la técnica (Fila 0, Y = 0)
        int rgb = rgbOf(t);
        g.setColor(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f, 1f);
        blit(g, x, y, size, t.type(), 0);

        // 2. CAPA SUPERIOR: Detalle/Brillo blanco puro SIN teñir (Fila 1, Y = CELL)
        g.setColor(1f, 1f, 1f, 1f); // Reset del tinte a blanco
        blit(g, x, y, size, t.type(), CELL);

        RenderSystem.disableBlend();
    }

    /**
     * Icono propio de una técnica (TechniqueDef.icon), UNA sola capa (sin el detalle blanco
     * separado que sí tiene el atlas compartido) — el contrato para un addon es "un PNG de
     * 20x20 (mismo tamaño de celda que el atlas base), blanco/gris, se tiñe entero", más simple
     * que replicar el sistema de dos capas. false si el recurso no existe (cae al atlas normal).
     */
    private static boolean drawCustom(GuiGraphics g, int x, int y, int size, String iconId, int rgb) {
        ResourceLocation id = ResourceLocation.tryParse(iconId);
        if (id == null) return false;
        if (!RaceTextureUtil.resourceExists(id)) return false;
        RenderSystem.enableBlend();
        g.setColor(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f, 1f);
        g.blit(id, x, y, size, size, 0f, 0f, CELL, CELL, CELL, CELL);
        g.setColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
        return true;
    }

    /**
     * Vista previa de un TIPO aún sin instancia (p. ej. una fila de MasterScreen antes de
     * comprarlo): sin KiTechnique no hay efecto ni color elegido todavía, así que dibuja solo
     * las dos capas base con el rgb que se le pase (para una técnica firma, el {@code rgb} es
     * fijo = {@code type.defaultRgb()}) y sin el marco de efecto.
     */
    public static void draw(GuiGraphics g, int x, int y, int size, KiTechniqueType type, int rgb) {
        if (size <= 0) return;

        String custom = type.icon();
        if (!custom.isEmpty() && drawCustom(g, x, y, size, custom, rgb)) return;

        if (!existsFor(type)) {
            int m3 = Math.max(2, size * 3 / 20);
            g.fill(x + m3, y + m3, x + size - m3, y + size - m3, 0xFF000000 | rgb);
            return;
        }

        RenderSystem.enableBlend();
        g.setColor(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f, 1f);
        blit(g, x, y, size, type, 0);

        g.setColor(1f, 1f, 1f, 1f);
        blit(g, x, y, size, type, CELL);

        RenderSystem.disableBlend();
    }

    /** blit de una fila (v = 0 base, CELL detalle) del PNG del tipo. */
    private static void blit(GuiGraphics g, int x, int y, int size, KiTechniqueType type, int v) {
        g.blit(locationOf(type), x, y, size, size, 0f, (float) v, CELL, CELL, TEX_W, TEX_H);
    }
}
