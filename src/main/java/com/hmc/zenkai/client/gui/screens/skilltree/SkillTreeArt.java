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
    /** Iconos de las skills del árbol por maestro (tools/gen_skill_icons_atlas.py): 256x256, celdas de 32. */
    static final ResourceLocation SKILL_ICONS = tex("skill_icons.png");

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
    // (184,32) está VACÍO a propósito (antes la bola de dragón, retirada); (200,32) es la llama de MND.
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
            "ssj1", "ssj2", "ssj3", "ssj4", "ssj_god", "ssj_blue", "ssj_rose",
            "oozaru", "super_oozaru", "human_super", "human_ultra", "human_indomitable_spirit",
            "human_god",
            "namek_giant", "namek_orange", "namek_god", "second_form", "third_form",
            "final_form", "golden_form",
            "black_form", "fifth_form", "golden_fifth_form", "black_fifth_form",
            "majin_pure_evil", "majin_super", "majin_harmony",
            "majin_pure_chaos", "majin_god", "potential_unlock", "beast", "ultra_instinct_sign",
            "ultra_instinct", "ultra_ego", "node_god_ki",
            "node_arcosian_power", "node_ultra_instinct", "node_ultra_ego", "unknown", "arcosian_god",
            "ssj_blue_evolution", "ssj_rose_evolution", "human_flow",
            "ikari", "ssj_green", "ssj_green_max", "human_legendary", "namek_legendary", "arcosian_legendary",
            "majin_legendary", "namek_super");
    // Sin cabezas *_base (borradas 2026-11-12): el nodo raíz dibuja la cara de la skin del jugador.

    static final int UNKNOWN_ICON = ICON_ORDER.indexOf("unknown");

    /** Celda del icono de un nodo; {@link #UNKNOWN_ICON} si no tiene (forma de datapack). */
    static int iconIndex(Node n, Race race) {
        String key;
        if (n.kind() == Kind.ROOT) {
            key = "unknown"; // la Base dibuja la cara de la skin (SkillTreeScreen); esto solo es la reserva
        } else if (n.form() != null && Zenkai.MOD_ID.equals(n.form().getNamespace())) {
            key = n.form().getPath();
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

    /** Icono resuelto de una forma: una textura PROPIA (32x32, {@code texture != null}) o una celda del atlas. */
    public record FormIcon(@org.jetbrains.annotations.Nullable ResourceLocation texture, int cell, int texW, int texH) {
        FormIcon(@org.jetbrains.annotations.Nullable ResourceLocation texture, int cell) { this(texture, cell, ICON_TEX, ICON_TEX); }
        public boolean generic() { return texture == null && cell == UNKNOWN_ICON; }
    }

    /** Tamaño {ancho, alto} del icono propio de una forma, o {0, 0} si no lo tiene. */
    private static final java.util.Map<ResourceLocation, int[]> CUSTOM_ICON = new java.util.concurrent.ConcurrentHashMap<>();

    /** Lee una vez el PNG de icono propio: cualquier tamaño cuadrado vale (16, 32, 64...); uno no cuadrado se avisa y se recorta. */
    private static int[] readCustomIcon(ResourceLocation tex) {
        var res = net.minecraft.client.Minecraft.getInstance().getResourceManager().getResource(tex);
        if (res.isEmpty()) return new int[]{0, 0};
        try (var in = res.get().open(); var img = com.mojang.blaze3d.platform.NativeImage.read(in)) {
            if (img.getWidth() != img.getHeight()) {
                Zenkai.LOGGER.warn("[Zenkai] El icono de forma {} mide {}x{}: tiene que ser cuadrado (32x32 recomendado); se recorta a la esquina.",
                        tex, img.getWidth(), img.getHeight());
            }
            return new int[]{img.getWidth(), img.getHeight()};
        } catch (Exception ex) {
            Zenkai.LOGGER.warn("[Zenkai] No se pudo leer el icono de forma {}: {}", tex, ex.toString());
            return new int[]{0, 0};
        }
    }

    /** Vacía la caché de «¿existe el icono propio de esta forma?» (se llama al recargar recursos, F3+T). */
    public static void clearCustomIcons() { CUSTOM_ICON.clear(); }

    /**
     * Icono de una forma, en este orden: (1) la textura propia {@code assets/<ns>/textures/gui/form_icons/<ruta>.png} si
     * el autor la trae (también sirve para que un resource pack cambie un icono del mod), (2) la celda del atlas si es una
     * forma del mod, (3) el {@code icon_default} del JSON de la forma (el id de otra forma de la que se presta el icono; se
     * sigue hasta 2 saltos), (4) el icono genérico «?» teñido con el aura. Lo usan el árbol y la HUD.
     */
    public static FormIcon formIcon(@org.jetbrains.annotations.Nullable ResourceLocation form) {
        return resolveIcon(form, 0);
    }

    private static FormIcon resolveIcon(@org.jetbrains.annotations.Nullable ResourceLocation form, int depth) {
        if (form == null) return new FormIcon(null, UNKNOWN_ICON);
        ResourceLocation tex = FormDef.iconTexture(form);
        int[] size = CUSTOM_ICON.computeIfAbsent(form, k -> readCustomIcon(tex));
        if (size[0] > 0) return new FormIcon(tex, UNKNOWN_ICON, size[0], size[1]);
        int cell = iconIndexOf(form);
        if (cell != UNKNOWN_ICON) return new FormIcon(null, cell);
        FormDef d = FormDef.get(form);
        if (d != null && !d.iconDefault().isEmpty() && depth < 2) {
            ResourceLocation ref = ResourceLocation.tryParse(d.iconDefault());
            if (ref != null && !ref.equals(form)) return resolveIcon(ref, depth + 1);
        }
        return new FormIcon(null, UNKNOWN_ICON);
    }

    /** Dibuja el icono propio de una forma (textura suelta de 32x32), con el mismo tinte/brillo que las celdas del atlas. */
    static void customIcon(GuiGraphics g, FormIcon icon, float x, float y, float r, float gr, float b) {
        int side = Math.min(icon.texW(), icon.texH());   // cuadrado: el lado menor; el icono ocupa lo mismo que una celda (16 px de GUI)
        var pose = g.pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        float k = (ICON_TEX * 0.5f) / side;
        pose.scale(k, k, 1f);
        RenderSystem.enableBlend();
        g.setColor(r, gr, b, 1f);
        g.blit(icon.texture(), 0, 0, 0, 0, side, side, icon.texW(), icon.texH());
        g.setColor(1f, 1f, 1f, 1f);
        pose.popPose();
    }

    /** Tinte del icono genérico: el color de aura de la forma (blanco si no tiene). */
    static int unknownTint(Node n) {
        FormDef d = n.form() == null ? null : FormDef.get(n.form());
        return d != null && d.auraRgb() >= 0 ? d.auraRgb() : 0xFFFFFF;
    }

    /** Orden de celdas de skill_icons.png (== CELLS de gen_skill_icons_atlas.py), por id de skill. */
    private static final List<String> SKILL_ICON_ORDER = List.of(
            "run", "fly", "quick_step", "instant_transmission", "ki_sense", "ki_control", "meditation", "ki_block",
            "ki_fist", "ki_infuse", "parry_mastery", "kaioken", "god_ki", "master_unknown", "zenkai_passive",
            "regen_passive", "coldblood_passive", "ki_sword", "ki_axe", "ki_mace", "ki_hammer", "ki_scythe",
            "ki_shield", "potential_passive", "elastic_passive");

    /**
     * Celda de {@code skill_icons.png} del nodo de la pasiva de cada raza. Esas celdas no son skills de
     * {@code zenkai_skills} (la pasiva es un nodo del árbol, {@code node/race_<raza>}), así que hasta el
     * 2026-11-12 no las pintaba nadie y el nodo salía con el «?» teñido.
     */
    static int passiveIconIndex(Race race) {
        if (race == null) return -1;
        return skillIconIndex(switch (race) {
            case SAIYAN -> "zenkai_passive";
            case NAMEKIAN -> "regen_passive";
            case ARCOSIAN -> "coldblood_passive";
            case HUMAN -> "potential_passive";
            case MAJIN -> "elastic_passive";
        });
    }

    /** Celda del icono de una skill por su id, o -1 si todavía no tiene (usa entonces el «?» tintado). */
    static int skillIconIndex(String skillId) {
        return skillId == null ? -1 : SKILL_ICON_ORDER.indexOf(skillId);
    }

    /** Dibuja el icono propio de una skill (a color pleno, sin tinte salvo el brillo {@code b}). */
    static void skillIcon(GuiGraphics g, int index, float x, float y, float b) {
        int u = (index % 8) * ICON_TEX, v = (index / 8) * ICON_TEX;
        blitTinted(g, SKILL_ICONS, x, y, u, v, ICON_TEX, ICON_TEX, ICONS_SIZE, ICONS_SIZE, b, b, b, 1f);
    }

    static void icon(GuiGraphics g, int index, float x, float y, float r, float gr, float b) {
        int u = (index % 8) * ICON_TEX, v = (index / 8) * ICON_TEX;
        blitTinted(g, ICONS, x, y, u, v, ICON_TEX, ICON_TEX, ICONS_SIZE, ICONS_SIZE, r, gr, b, 1f);
    }
}
