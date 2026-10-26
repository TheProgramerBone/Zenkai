package com.hmc.zenkai.client.gui.screens.skilltree;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.feature.Race;
import com.hmc.zenkai.feature.forms.FormDef;
import com.hmc.zenkai.feature.forms.FormIds;
import com.hmc.zenkai.feature.skilltree.SkillTreeMigration;
import com.hmc.zenkai.feature.skilltree.SkillTreeNodes.Kind;
import com.hmc.zenkai.feature.skilltree.SkillTreeNodes.Node;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Locale;

/**
 * Arte del lienzo del skill tree (F2b): texturas, temas de fondo por rama y celdas de los atlas.
 * Las texturas salen de tools/gen_skill_tree_art.py (fondos, marcos, HUD) y
 * tools/gen_skill_tree_icons.py (iconos de nodo); las coordenadas de aquí están DUPLICADAS de
 * esos scripts a propósito (un script no lee Java): si cambian allí, cambian aquí.
 *
 * Densidad: el arte del árbol va a 2 texeles por píxel de GUI (el menú fija GUI Scale 2, ver
 * ZenkaiGuiScaleLock), así que {@link #blit} dibuja cada región a la MITAD de su tamaño en
 * texeles y sale 1:1 en pantalla.
 */
final class SkillTreeArt {
    private SkillTreeArt() {}

    static final ResourceLocation ICONS = tex("node_icons.png");
    static final ResourceLocation FRAMES = tex("frames.png");
    static final ResourceLocation HUD = tex("hud.png");
    /** Retratos de los maestros (tools/gen_master_portraits.py): celdas de 32 en el orden de SkillsTreeScreen.MASTERS. */
    static final ResourceLocation MASTER_PORTRAITS = tex("master_portraits.png");
    static final int MASTER_PORTRAITS_W = 128, MASTER_PORTRAITS_H = 32;

    static final int ICONS_SIZE = 256;
    static final int FRAMES_W = 384, FRAMES_H = 96;
    static final int HUD_W = 256, HUD_H = 128;

    /** Lado de una celda en texeles. */
    static final int ICON_TEX = 32, FRAME_TEX = 48;

    // ── hud.png (== gen_skill_tree_art.py, función hud) ──
    static final int PLATE_U = 0, PLATE_V = 0, PLATE_PIECE = 8, PLATE_H = 28;
    static final int BUTTON_U = 0, BUTTON_HOVER_U = 48, BUTTON_V = 32, BUTTON_TEX = 48;
    static final int LINK_U = 96, LINK_V = 32, LINK_W = 24, LINK_H = 30;
    static final int LINK_SIDE_U = 120, LINK_SIDE_W = 8;
    static final int SEAL_U = 128, SEAL_V = 32, SEAL_W = 40, SEAL_H = 44;
    static final int LOCK_U = 168, LOCK_V = 32, LOCK_W = 14, LOCK_H = 16;
    // (184,32) y (200,32): glifos de TP y MND, hoy sin usar (las placas llevan el nombre escrito).
    static final int QUESTION_U = 216, QUESTION_V = 32, QUESTION_W = 14, QUESTION_H = 16;

    private static ResourceLocation tex(String path) {
        return ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/skill_tree/" + path);
    }

    /** Región de {@code w x h} texeles dibujada a la mitad (1 px de GUI = 2 texeles). */
    static void blit(GuiGraphics g, ResourceLocation tex, float x, float y, int u, int v, int w, int h,
                     int texW, int texH) {
        var pose = g.pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        pose.scale(0.5f, 0.5f, 1f);
        g.blit(tex, 0, 0, u, v, w, h, texW, texH);
        pose.popPose();
    }

    static void blitTinted(GuiGraphics g, ResourceLocation tex, float x, float y, int u, int v, int w, int h,
                           int texW, int texH, float r, float gr, float b, float a) {
        RenderSystem.enableBlend();
        g.setColor(r, gr, b, a);
        blit(g, tex, x, y, u, v, w, h, texW, texH);
        g.setColor(1f, 1f, 1f, 1f);
    }

    // ── Temas de fondo por rama ──────────────────────────────────────────────

    /**
     * Fondo de cada rama del árbol (== THEMES de gen_skill_tree_art.py). Escala con el poder:
     * galaxia (Base) → energía de la raza (formas normales) → brasa (formas mortales con tier)
     * → divino (tras God Ki); las rutas universales tienen el suyo.
     */
    enum Theme {
        COSMOS(0xB8C4FF), ENERGY_SAIYAN(0xFFE49A), ENERGY_HUMAN(0xB8DCFF), ENERGY_NAMEKIAN(0xC0FFB8),
        ENERGY_ARCOSIAN(0xE0C4FF), ENERGY_MAJIN(0xFFC8E8),
        ASCENDED(0xFFC49C), DIVINE(0xFFC4CC), MYSTIC(0xE4E0FF), ULTRA_INSTINCT(0xD0DCFF), ULTRA_EGO(0xE0CCFF),
        /** Camino del Legendario (F6): verde caótico, para ssj_green* cuando existan. */
        LEGENDARY(0xB8FFB0);

        final ResourceLocation back, mid;
        /** Color de las estrellas animadas del tema (RGB, sin alfa). */
        final int star;

        Theme(int star) {
            this.star = star;
            String n = name().toLowerCase(Locale.ROOT);
            back = tex("backgrounds/" + n + "_back.png");
            mid = tex("backgrounds/" + n + "_mid.png");
        }

        static Theme energy(Race race) {
            if (race == null) return COSMOS;
            return switch (race) {
                case SAIYAN -> ENERGY_SAIYAN;
                case HUMAN -> ENERGY_HUMAN;
                case NAMEKIAN -> ENERGY_NAMEKIAN;
                case ARCOSIAN -> ENERGY_ARCOSIAN;
                case MAJIN -> ENERGY_MAJIN;
            };
        }
    }

    /** Lado del mosaico de fondo en px de GUI (256 texeles a densidad 2). */
    static final int BG_TILE = 128;

    // ── Marcos ───────────────────────────────────────────────────────────────

    /** Columna de frames.png (== FRAME_RAMPS de gen_skill_tree_art.py). */
    enum Frame { SAIYAN, HUMAN, NAMEKIAN, ARCOSIAN, MAJIN, UNIVERSAL, SECRET, ULTRA_EGO }

    static Frame frameOf(Race race) {
        if (race == null) return Frame.UNIVERSAL;
        return switch (race) {
            case SAIYAN -> Frame.SAIYAN;
            case HUMAN -> Frame.HUMAN;
            case NAMEKIAN -> Frame.NAMEKIAN;
            case ARCOSIAN -> Frame.ARCOSIAN;
            case MAJIN -> Frame.MAJIN;
        };
    }

    /** Color de acento de cada familia (aristas encendidas, letreros): el tono medio del marco. */
    static int accent(Frame f) {
        return switch (f) {
            case SAIYAN -> 0xFFE0A030;
            case HUMAN -> 0xFF4A90D9;
            case NAMEKIAN -> 0xFF4FBF5A;
            case ARCOSIAN -> 0xFFA060E0;
            case MAJIN -> 0xFFE070B0;
            case UNIVERSAL -> 0xFFC8C8DC;
            case SECRET -> 0xFFC04060;
            case ULTRA_EGO -> 0xFF9A3CFF;
        };
    }

    // ── Iconos ───────────────────────────────────────────────────────────────

    /** Orden de celdas de node_icons.png (== CELLS de gen_skill_tree_icons.py). */
    private static final List<String> ICON_ORDER = List.of(
            "saiyan_base", "ssj1", "ssj2", "ssj3", "ssj4", "ssj_god", "ssj_blue", "ssj_rose",
            "oozaru", "super_oozaru", "human_base", "human_super", "human_ultra", "human_indomitable_spirit",
            "human_god", "namekian_base",
            "namek_giant", "namek_orange", "namek_god", "arcosian_base", "second_form", "third_form",
            "final_form", "golden_form",
            "black_form", "fifth_form", "golden_fifth_form", "black_fifth_form", "majin_base",
            "majin_pure_evil", "majin_super", "majin_harmony",
            "majin_pure_chaos", "majin_god", "potential_unlock", "beast", "ultra_instinct_sign",
            "ultra_instinct", "ultra_ego", "node_god_ki",
            "node_arcosian_power", "node_ultra_instinct", "node_ultra_ego", "unknown", "arcosian_god",
            "ssj_blue_evolution", "ssj_rose_evolution");

    static final int UNKNOWN_ICON = ICON_ORDER.indexOf("unknown");

    /** Celda del icono de un nodo; {@link #UNKNOWN_ICON} si no tiene (forma de datapack). */
    static int iconIndex(Node n, Race race) {
        String key;
        if (n.kind() == Kind.ROOT) {
            key = race == null ? "unknown" : race.name().toLowerCase(Locale.ROOT) + "_base";
        } else if (n.form() != null && Zenkai.MOD_ID.equals(n.form().getNamespace())) {
            key = n.form().getPath();
        } else if (SkillTreeMigration.NODE_NAMEK_GIANT.equals(n.id())) {
            key = "namek_giant"; // el icono de la antigua forma
        } else if (SkillTreeMigration.NODE_NAMEK_GIANT.equals(n.id())) {
            key = "namek_giant"; // el icono de la antigua forma
        } else if (SkillTreeMigration.NODE_POTENTIAL_UNLOCK.equals(n.id())) {
            key = FormIds.POTENTIAL_UNLOCK.getPath();
        } else if (n.id().getPath().startsWith("node/")) {
            key = "node_" + n.id().getPath().substring("node/".length());
        } else {
            key = "unknown";
        }
        int i = ICON_ORDER.indexOf(key);
        return i < 0 ? UNKNOWN_ICON : i;
    }

    /** Celda del icono de una forma por su id; {@link #UNKNOWN_ICON} si no tiene. Misma lista que {@link #iconIndex}. */
    static int iconIndexOf(@org.jetbrains.annotations.Nullable ResourceLocation form) {
        if (form == null || !Zenkai.MOD_ID.equals(form.getNamespace())) return UNKNOWN_ICON;
        int i = ICON_ORDER.indexOf(form.getPath());
        return i < 0 ? UNKNOWN_ICON : i;
    }

    /** Tinte del icono genérico: el color de aura de la forma (blanco si no tiene). */
    static int unknownTint(Node n) {
        FormDef d = n.form() == null ? null : FormDef.get(n.form());
        return d != null && d.auraRgb() >= 0 ? d.auraRgb() : 0xFFFFFF;
    }

    static void icon(GuiGraphics g, int index, float x, float y, float r, float gr, float b) {
        int u = (index % 8) * ICON_TEX, v = (index / 8) * ICON_TEX;
        blitTinted(g, ICONS, x, y, u, v, ICON_TEX, ICON_TEX, ICONS_SIZE, ICONS_SIZE, r, gr, b, 1f);
    }
}
