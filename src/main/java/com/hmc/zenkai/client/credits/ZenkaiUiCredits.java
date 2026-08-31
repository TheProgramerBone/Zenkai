package com.hmc.zenkai.client.credits;

import java.util.ArrayList;
import java.util.List;

/**
 * Créditos de UI, íconos y animaciones — puramente cliente, sin DataMap.
 * Hermano de ModDataMaps.MODEL_CREDITS (créditos de modelos/texturas de ítems), pero con un
 * almacén distinto a propósito: un DataMap solo puede colgar de un objeto de un Registry
 * (Registries.ITEM en ese caso), y una Screen, una celda de un atlas de íconos o un clip de
 * animación NO son objetos de ningún Registry — no hay dónde enganchar uno. Tampoco hace
 * falta el resto de la maquinaria que sí necesitan los créditos de ítems (datagen, JSON de
 * datapack, sincronización a red): esas tres cosas existen ahí porque el ítem es un concepto
 * compartido servidor/cliente, mientras que una pantalla de GUI, un ícono o un clip de PAL son
 * 100% cliente — nadie en el servidor necesita saber quién los dibujó o animó. Por eso esto es
 * una lista Java poblada a mano, mismo espíritu que ModDataMapProvider.credit(...) pero sin el
 * paso de datagen.
 * Leído por CreditsScreen; author/detail/displayName son cadenas literales (no claves de
 * traducción), igual que ModDataMaps.ModelCredit — son nombres propios y rutas técnicas, no
 * texto de interfaz a localizar.
 */
public final class ZenkaiUiCredits {
    private ZenkaiUiCredits() {}

    public enum Category { UI, ICONS, ANIMATIONS }

    /**
     * iconU/iconV: esquina de la celda en textures/gui/icons.png (256x256, grid de 20px) para
     * pintar una miniatura real junto a la fila en CreditsScreen — igual que Modelos pinta el
     * ItemStack. -1 si la entrada no tiene una celda única que mostrar (p. ej. technique_icons_atlas,
     * que son varios íconos en OTRO archivo, o cualquier entrada de UI/Animaciones).
     */
    public record Entry(Category category, String displayName, String author, String detail,
                         int iconU, int iconV) {
        public boolean hasAtlasIcon() { return iconU >= 0 && iconV >= 0; }
    }

    /** Autor sin confirmar todavía. NUNCA sustituir por un nombre inventado: rellenar a mano. */
    public static final String UNKNOWN_AUTHOR = "?";

    public static final String AUTHOR = "TheGrim0508";

    private static final List<Entry> ENTRIES = new ArrayList<>();

    private static void credit(Category cat, String displayName, String author, String detail) {
        ENTRIES.add(new Entry(cat, displayName, author, detail, -1, -1));
    }

    /** Entrada de Category.ICONS con miniatura real: (u,v) es la celda en icons.png. */
    private static void creditIcon(String displayName, String author, int u, int v, String detail) {
        ENTRIES.add(new Entry(Category.ICONS, displayName, author, detail, u, v));
    }

    static {
        // ── UI / pantallas (client/gui/screens/) ────────────────────────────────
        // Distinguir dos cosas que NO son lo mismo: la TEXTURA de fondo compartida (un asset
        // concreto, con autor real) y el DISEÑO de cada pantalla individual (layout/widgets,
        // hecho por el propio autor del mod) — mezclarlas atribuiría el diseño de las pantallas
        // a quien solo hizo la textura de fondo.
        credit(Category.UI, "common_screen.png", "KunziteGem", "Menu panel background texture");
        // Una línea por Screen: diseño propio, TheGrim0508.
        credit(Category.UI, "MasterScreen", AUTHOR, "Screen design");
        credit(Category.UI, "StatsScreen", AUTHOR, "Screen design");
        credit(Category.UI, "SkillsScreen", AUTHOR, "Screen design");
        credit(Category.UI, "KiTechniquesScreen", AUTHOR, "Screen design");
        credit(Category.UI, "PhysicalScreen", AUTHOR, "Screen design");
        credit(Category.UI, "MasteryScreen", AUTHOR, "Screen design");
        credit(Category.UI, "PartyScreen", AUTHOR, "Screen design");
        credit(Category.UI, "ClientConfigScreen", AUTHOR, "Screen design");
        credit(Category.UI, "TechniqueEditScreen", AUTHOR, "Screen design");
        credit(Category.UI, "AppearanceScreen", AUTHOR, "Screen design");
        credit(Category.UI, "HeadAppearanceScreen", AUTHOR, "Screen design");
        credit(Category.UI, "BodyColorsScreen", AUTHOR, "Screen design");
        credit(Category.UI, "RaceSelectionScreen", AUTHOR, "Screen design");
        credit(Category.UI, "StyleSelectionScreen", AUTHOR, "Screen design");
        credit(Category.UI, "WeightScreen", AUTHOR, "Screen design");
        credit(Category.UI, "HudPlacementScreen", AUTHOR, "Screen design");
        credit(Category.UI, "NpcMarkerScreen", AUTHOR, "Screen design");
        credit(Category.UI, "ScouterBenchScreen", AUTHOR, "Screen design");
        credit(Category.UI, "EnergyGeneratorScreen", AUTHOR, "Screen design");
        credit(Category.UI, "ShenlongWishScreen", AUTHOR, "Screen design");
        credit(Category.UI, "ImmortalWishScreen", AUTHOR, "Screen design");
        credit(Category.UI, "RevivePetWishScreen", AUTHOR, "Screen design");
        credit(Category.UI, "RevivePlayerWishScreen", AUTHOR, "Screen design");
        credit(Category.UI, "StackWishScreen", AUTHOR, "Screen design");
        credit(Category.UI, "EnchantVillagerWishScreen", AUTHOR, "Screen design");
        credit(Category.UI, "TrainingPointsWishScreen", AUTHOR, "Screen design");

        // ── Íconos (textures/gui/icons.png, textures/gui/technique_icons.png) ───
        // Una línea por celda de atlas identificada como arte propio (no vainilla), con su (u,v)
        // real para que CreditsScreen pinte la miniatura tal cual se ve en el atlas — mismo
        // espíritu que Modelos pinta el ItemStack. El usuario decide caso a caso si un ícono
        // generado por script (tools/gen_*.py) cuenta como "con autor" o no.
        creditIcon("icon_head", AUTHOR, 40, 80, "icons.png (40,80) — AppearanceScreen.ICON_HEAD_U/V");
        creditIcon("icon_body_colors", AUTHOR, 60, 80, "icons.png (60,80) — AppearanceScreen.ICON_BODY_U/V");
        creditIcon("icon_gender_male", AUTHOR, 80, 80, "icons.png (80,80) — hand-painted");
        creditIcon("icon_gender_female", AUTHOR, 100, 80, "icons.png (100,80) — hand-painted");
        creditIcon("icon_reset_view", AUTHOR, 0, 100, "icons.png (0,100) — hand-painted");
        creditIcon("icon_master_techniques", AUTHOR, 0, 80, "icons.png (0,80) — MasterScreen.ICON_TECHNIQUES_U/V");
        creditIcon("icon_master_services", AUTHOR, 20, 80, "icons.png (20,80) — MasterScreen.ICON_SERVICES_U/V");
        creditIcon("icon_friendly_fire_off", AUTHOR, 0, 60, "icons.png (0,60)");
        creditIcon("icon_friendly_fire_on", AUTHOR, 20, 60, "icons.png (20,60)");
        creditIcon("icon_party_invite", AUTHOR, 40, 60, "icons.png (40,60)");
        creditIcon("icon_party_config", AUTHOR, 80, 60, "icons.png (80,60)");
        creditIcon("icon_credits_tab", AUTHOR, 120, 80, "icons.png (120,80) — ZenkaiTab.CREDITS");
        // Sin (u,v) propio: son varios íconos en OTRO archivo (technique_icons.png), no una celda.
        credit(Category.ICONS, "technique_icons_atlas", "No Specific Author", "technique_icons.png — one icon per KiTechniqueType");

        // ── Animaciones de jugador (assets/zenkai/player_animations/*.animation.json) ──
        // Una entrada por clip. Reproducidos por PAL, autoría son quienes los modelan en
        // Blockbench (ver skill add-player-animation).
        credit(Category.ANIMATIONS, "block", AUTHOR, "player_animations/block.animation.json");
        credit(Category.ANIMATIONS, "combat_idle_martial_artist", AUTHOR, "player_animations/combat_idle_martial_artist.animation.json");
        credit(Category.ANIMATIONS, "combat_idle_martial_artist_start", AUTHOR, "player_animations/combat_idle_martial_artist_start.animation.json");
        credit(Category.ANIMATIONS, "combat_idle_spiritualist", AUTHOR, "player_animations/combat_idle_spiritualist.animation.json");
        credit(Category.ANIMATIONS, "combat_idle_spiritualist_start", AUTHOR, "player_animations/combat_idle_spiritualist_start.animation.json");
        credit(Category.ANIMATIONS, "combat_idle_warrior", AUTHOR, "player_animations/combat_idle_warrior.animation.json");
        credit(Category.ANIMATIONS, "combat_idle_warrior_start", AUTHOR, "player_animations/combat_idle_warrior_start.animation.json");
        credit(Category.ANIMATIONS, "fly", AUTHOR, "player_animations/fly.animation.json");
        credit(Category.ANIMATIONS, "fly_boost", AUTHOR, "player_animations/fly_boost.animation.json");
        credit(Category.ANIMATIONS, "fly_start", AUTHOR, "player_animations/fly_start.animation.json");
        credit(Category.ANIMATIONS, "fly_stop", AUTHOR, "player_animations/fly_stop.animation.json");
        credit(Category.ANIMATIONS, "ki_attack_1", AUTHOR, "player_animations/ki_attack_1.animation.json");
        credit(Category.ANIMATIONS, "ki_attack_2", AUTHOR, "player_animations/ki_attack_2.animation.json");
        credit(Category.ANIMATIONS, "ki_attack_3", AUTHOR, "player_animations/ki_attack_3.animation.json");
        credit(Category.ANIMATIONS, "ki_attack_4", AUTHOR, "player_animations/ki_attack_4.animation.json");
        credit(Category.ANIMATIONS, "ki_attack_5", AUTHOR, "player_animations/ki_attack_5.animation.json");
        credit(Category.ANIMATIONS, "ki_attack_6", AUTHOR, "player_animations/ki_attack_6.animation.json");
        credit(Category.ANIMATIONS, "ki_attack_7", AUTHOR, "player_animations/ki_attack_7.animation.json");
        credit(Category.ANIMATIONS, "ki_attack_8", AUTHOR, "player_animations/ki_attack_8.animation.json");
        credit(Category.ANIMATIONS, "ki_attack_9", AUTHOR, "player_animations/ki_attack_9.animation.json");
        credit(Category.ANIMATIONS, "ki_barrier", AUTHOR, "player_animations/ki_barrier.animation.json");
        credit(Category.ANIMATIONS, "ki_charge", AUTHOR, "player_animations/ki_charge.animation.json");
        credit(Category.ANIMATIONS, "ki_charge_start", AUTHOR, "player_animations/ki_charge_start.animation.json");
        credit(Category.ANIMATIONS, "ki_explosion", AUTHOR, "player_animations/ki_explosion.animation.json");
        credit(Category.ANIMATIONS, "phys_barrage", AUTHOR, "player_animations/phys_barrage.animation.json");
        credit(Category.ANIMATIONS, "phys_dash_punch", AUTHOR, "player_animations/phys_dash_punch.animation.json");
        credit(Category.ANIMATIONS, "phys_heavy_blow", AUTHOR, "player_animations/phys_heavy_blow.animation.json");
        credit(Category.ANIMATIONS, "phys_kiai", AUTHOR, "player_animations/phys_kiai.animation.json");
        credit(Category.ANIMATIONS, "transformation1", AUTHOR, "player_animations/transformation1.animation.json");
        credit(Category.ANIMATIONS, "transformation2", AUTHOR, "player_animations/transformation2.animation.json");
    }

    public static List<Entry> byCategory(Category c) {
        return ENTRIES.stream().filter(e -> e.category() == c).toList();
    }
}
