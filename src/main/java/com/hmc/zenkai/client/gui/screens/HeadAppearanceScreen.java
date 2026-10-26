package com.hmc.zenkai.client.gui.screens;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.config.ServerConfig;
import com.hmc.zenkai.feature.player.PlayerVisualAttachment;
import com.hmc.zenkai.feature.race.hair.CustomHairLibrary;
import com.hmc.zenkai.feature.race.hair.CustomHairLibraryPacket;
import com.hmc.zenkai.feature.race.hair.HairCatalog;
import com.hmc.zenkai.feature.race.hair.HairPackSource;
import com.hmc.zenkai.feature.race.hair.HairStyleDef;
import com.hmc.zenkai.feature.race.hair.strand.DmzHairCodec;
import com.hmc.zenkai.feature.race.hair.strand.HairCodeException;
import com.hmc.zenkai.feature.race.hair.strand.HairCodes;
import com.hmc.zenkai.feature.race.hair.strand.HairPackWriter;
import com.hmc.zenkai.client.gui.Favorites;
import com.hmc.zenkai.client.gui.PanelText;
import com.hmc.zenkai.client.gui.ScreenTitle;
import com.hmc.zenkai.client.gui.NineSlice;
import com.hmc.zenkai.client.gui.ZenkaiPalette;
import com.hmc.zenkai.client.gui.buttons.ArrowIconButton;
import com.hmc.zenkai.client.gui.buttons.AtlasIconButton;
import com.hmc.zenkai.client.gui.buttons.TextOnlyButton;
import com.hmc.zenkai.client.gui.widgets.ColorBoxButton;
import com.hmc.zenkai.client.gui.widgets.ColorPickerWidget;
import com.hmc.zenkai.client.gui.widgets.FacePartTile;
import com.hmc.zenkai.client.gui.widgets.HairTile;
import com.hmc.zenkai.feature.Race;
import com.hmc.zenkai.feature.race.FaceBans;
import com.hmc.zenkai.feature.race.face.FaceCatalog;
import com.hmc.zenkai.feature.race.face.FaceCategory;
import com.hmc.zenkai.feature.race.face.FacePartDef;
import com.hmc.zenkai.registry.ZenkaiDataAttachments;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

/**
 * Sección "Head" del hub de apariencia ({@link AppearanceScreen}): ojos, cejas, nariz, boca (del
 * catálogo FaceCatalog) y pelo (catálogo HairCatalog + biblioteca de peinados importados de
 * códigos DMZ/DBC, que se pegan en la propia pestaña Pelo; ver CustomHairLibrary).
 *
 * REDISEÑO S3 (2026-10-01, .claude/docs/customizacion-cara-y-pelo.md (decisiones cerradas)): antes eran
 * cuatro filas de flechas con etiquetas "Eyes N", y con el preview del cuerpo entero la cabeza
 * medía ~22 px — los cinco ojos apenas se distinguían entre sí (medido con render en
 * .claude/workbench/auditoria-2026-10-01/). Ahora:
 *  · una pestaña por categoría (subrayado = activa, TextOnlyButton.selected — no asAction(),
 *    que ensancha el texto, ver gui.md);
 *  · una cuadrícula de MINIATURAS: cada casilla es la cara del propio jugador con esa opción
 *    puesta y el resto de sus rasgos (FaceThumbnails, compuesta en vivo — sin assets que
 *    mantener); nombre traducido bajo la cuadrícula y en el tooltip;
 *  · preview 3D con CÁMARA A LA CARA por defecto (vista frontal fija, la que permite comparar),
 *    conmutables a cuerpo entero; la rueda hace zoom en los dos modos;
 *  · color de ojos con heterocromía opcional (ojo derecho propio, -1 = igual que el izquierdo).
 * Las opciones que la raza no puede elegir no se enseñan (FaceBans.choices).
 *
 * SINCRONIZACIÓN (igual que antes): se crea SIEMPRE de cero desde el hub; cada cambio se escribe
 * directo en PlayerVisualAttachment (applyPreview) para que preview y miniaturas lo vean al
 * instante; "Back" vuelve al MISMO hub marcando goingToHub, "Next" va a StyleSelectionScreen
 * marcando goingNext, y removed() solo revierte al snapshot si no se marcó ninguno — mismo
 * contrato que RaceSelectionScreen/StyleSelectionScreen (ver gui.md, entrada de AppearanceScreen).
 */
public class HeadAppearanceScreen extends Screen {

    private static final ResourceLocation BG =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/screens/common_screen.png");
    private static final ResourceLocation TEX_BTN =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/buttons/btn_wide.png");

    private static final int BG_W = 256;
    private static final int BG_H = 256;
    // Beige real de common_screen.png: x=12..243 (el marco ocupa 0..11 y 244..255). Antes era
    // 10/245, copiado de las pantallas viejas, y las flechas/botones pisaban el borde. Ver gui.md.
    private static final int IN_X1 = 12;
    private static final int IN_Y1 = 10;
    private static final int IN_X2 = 243;
    private static final int IN_Y2 = 240;
    private static final int BTN_BAR_Y = 260;
    private static final int BTN_W     = 60;
    private static final int PAD       = 6;

    // ── Pestañas ──
    private static final int TAB_Y = 16, TAB_H = 12;
    private static final int SEP1_Y = 31;

    // ── Columna izquierda: cuadrícula + nombre + controles ──
    private static final int LEFT_X = IN_X1 + PAD;                         // 18
    private static final int GRID_COLS = 3, GRID_ROWS = 3, GRID_GAP = 6;
    private static final int GRID_Y = 36;
    private static final int GRID_W = GRID_COLS * FacePartTile.SIZE + (GRID_COLS - 1) * GRID_GAP;   // 114
    private static final int GRID_H = GRID_ROWS * FacePartTile.SIZE + (GRID_ROWS - 1) * GRID_GAP;   // 114
    private static final int LEFT_R = LEFT_X + GRID_W;                     // 132
    private static final int PER_PAGE = GRID_COLS * GRID_ROWS;
    private static final int ARROW_W = 12;
    private static final int NAME_Y = GRID_Y + GRID_H + 5;                 // 155
    private static final int SEP2_Y = NAME_Y + 14;                         // 169
    private static final int ROW1_Y = SEP2_Y + 6;                          // 175
    private static final int ROW_H  = 16;
    private static final int VIEW_Y = IN_Y2 - 20;                          // 220
    private static final int COLOR_BOX_W = 12, COLOR_BOX_H = 10;
    /** Hueco de la casilla de heterocromía (casilla de 8 px + 2 de aire). */
    private static final int CHECK_W = 10;

    // ── Columna derecha: preview ──
    private static final int PV_X1 = LEFT_R + PAD;                         // 138
    private static final int PV_X2 = IN_X2 - PAD;                          // 237
    private static final int PV_Y1 = GRID_Y;
    private static final int PV_Y2 = IN_Y2 - 6;                            // 234

    /**
     * Cámara a la cara: InventoryScreen centra el recuadro en la altura bbHeight/2 + yOffset·escala
     * del jugador (comprobado en el source de 1.21.1), y la cabeza está a ~1.55 de 1.8 ⇒ 0.65.
     * El tamaño en pantalla NO depende de la escala de la forma (vanilla divide por getScale),
     * así que encuadra igual en Oozaru. FACE_SIZE: la cabeza (0.5 bloques) ≈ 70 px.
     */
    private static final float FACE_Y_OFFSET = 0.65f;
    private static final int FACE_SIZE = 140;
    private static final float BODY_Y_OFFSET = 0.0625f;
    private static final float BODY_SIZE_RATIO = 0.42f;
    private static final float ZOOM_MIN = 0.6f, ZOOM_MAX = 1.8f, ZOOM_STEP = 0.1f;
    // Celda de icons.png pintada a mano (lupa), ver gen_appearance_icons.py.
    private static final int ICON_RESET_VIEW_U = 0, ICON_RESET_VIEW_V = 100;

    private static final int COLOR_TITLE = ZenkaiPalette.LABEL_ON_PANEL;
    private static final int COLOR_VALUE = ZenkaiPalette.VALUE_ON_PANEL;
    private static final int COLOR_MUTED = ZenkaiPalette.MUTED_ON_PANEL;

    /** Pestañas: las cuatro categorías del catálogo + el pelo (aún fuera del catálogo). */
    private enum Tab {
        EYES(FaceCategory.EYES), EYEBROWS(FaceCategory.EYEBROWS), NOSE(FaceCategory.NOSE),
        MOUTH(FaceCategory.MOUTH), HAIR(null);
        @Nullable final FaceCategory category;
        Tab(@Nullable FaceCategory category) { this.category = category; }
        String key() { return "screen.zenkai.head.tab." + name().toLowerCase(java.util.Locale.ROOT); }
    }

    private enum ColorChannel { EYE, EYE_RIGHT, HAIR }

    /**
     * Pestaña y modo de vista recordados entre visitas: la pantalla se crea de cero cada vez, y un
     * campo de instancia volvería siempre a "Ojos"/"cara" (misma lección que StatsScreen.showStats).
     */
    private static Tab lastTab = Tab.EYES;
    private static boolean faceView = true;

    @Nullable private final AppearanceScreen hub;
    /**
     * Abierta con las tijeras (ScissorsItem): solo la pestaña Pelo, "Cancelar" deshace y "Hecho"
     * guarda al momento (UpdatePlayerVisualPacket), sin pasar por el resto del creador de personaje.
     */
    private boolean hairOnly;
    private final CompoundTag statsSnapshot;
    private final CompoundTag visualSnapshot;

    private boolean goingToHub = false, goingNext = false;
    private boolean stateLoaded = false;
    private int panelLeft, panelTop;
    private Race race = Race.HUMAN;

    private Tab tab = Tab.EYES;
    private int page = 0;

    /**
     * Skin predeterminada (Human/Saiyan con "Skin Predeterminada" en la pantalla de raza): se ve la
     * skin vanilla del jugador, sin el cuerpo de raza, y los rasgos de cara (ojos, cejas, nariz,
     * boca) se pintan SOBRE el cuerpo de raza, así que aquí no hacen nada. Solo queda editable el
     * pelo; el resto de pestañas se enseñan oscurecidas y bloqueadas, con tooltip.
     */
    private boolean vanillaSkin = false;
    /** Rectángulos (x, y, w, h) de las pestañas bloqueadas, para oscurecerlos en render. */
    private final List<int[]> lockedTabRects = new ArrayList<>();

    /**
     * Peinado elegido, en el formato de hairStyleId: "none", un id del catálogo o "custom:&lt;id&gt;"
     * (uno de la biblioteca de importados, CustomHairLibrary).
     */
    private String hairSel = PlayerVisualAttachment.HAIR_NONE;
    /**
     * Biblioteca de pelo (2026-10-12): cuadrícula como la de la cara, con carpetas. Carpeta abierta:
     * null = raíz; CUSTOM_FOLDER = importados (casilla "+"); si no, el id de la carpeta del catálogo
     * (HairStyleDef.folder, p. ej. la de DragonMineZ).
     */
    @Nullable private String hairFolder;
    private static final String CUSTOM_FOLDER = "#custom";
    private int hairPage = 0;
    /** Panel de importar abierto (carpeta Custom, casilla "+"). */
    private boolean importOpen;
    /** Textos del panel de importar: sobreviven a rebuildWidgets() (cambio de pestaña, picker...). */
    private String importDraft = "";
    private String importName = "";
    /** Resultado del último importar/borrar/exportar. */
    @Nullable private Component hairStatus;
    private boolean hairStatusError;
    private static final String HAIR_FAV = "hair";

    /** Una casilla de la biblioteca de pelo. */
    private record HairItem(HairTile.Kind kind, @Nullable String styleId, @Nullable ResourceLocation folder,
                            Component name) {}
    /** Rasgo elegido por categoría (id del FaceCatalog); sin entrada = ninguno. */
    private final EnumMap<FaceCategory, ResourceLocation> faceParts = new EnumMap<>(FaceCategory.class);
    private int eyeColor = 0xFF2E86C1, hairColor = 0xFF1A1A1A;
    /** Colores propios de los mechones (StrandColors) encendidos; ver PlayerVisualAttachment. */
    private boolean hairStrandColors = true;
    /** ¿El peinado elegido trae colores propios? Se recalcula al reconstruir (leerlo cuesta un SHA-1). */
    private boolean hairHasColors;
    /** Iris derecho: -1 = sin heterocromía (sigue al izquierdo). */
    private int eyeRightColor = -1;
    /** Estado de vista puro: nunca entra en los snapshots ni en removed(). */
    private float zoomMult = 1.0f;

    @Nullable private ColorChannel      activeChannel = null;
    @Nullable private ColorPickerWidget picker        = null;

    /** true si se llega con "Back" desde BodyColorsScreen: se abre en la ÚLTIMA pestaña. */
    private final boolean startAtLastTab;

    public HeadAppearanceScreen(AppearanceScreen hub,
                                @Nullable CompoundTag statsSnapshot, @Nullable CompoundTag visualSnapshot) {
        this(hub, statsSnapshot, visualSnapshot, false);
    }

    /** Tijeras: solo el pelo, guardando al pulsar "Hecho" (ver {@link #hairOnly}). */
    public static void openHairOnly() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        var stats  = mc.player.getData(ZenkaiDataAttachments.PLAYER_STATS.get());
        var visual = mc.player.getData(ZenkaiDataAttachments.PLAYER_VISUAL.get());
        HeadAppearanceScreen s = new HeadAppearanceScreen(null, stats.save(), visual.save(), false);
        s.hairOnly = true;
        mc.setScreen(s);
    }

    public HeadAppearanceScreen(@Nullable AppearanceScreen hub,
                                @Nullable CompoundTag statsSnapshot, @Nullable CompoundTag visualSnapshot,
                                boolean startAtLastTab) {
        super(Component.translatable("screen.zenkai.appearance.title"));
        this.hub            = hub;
        this.statsSnapshot  = statsSnapshot;
        this.visualSnapshot = visualSnapshot;
        this.startAtLastTab = startAtLastTab;
    }

    // ── Estado ───────────────────────────────────────────────────────────────

    /** Una sola vez por pantalla: init() se repite al cambiar de pestaña/página o redimensionar. */
    private void loadState() {
        Minecraft mc = Minecraft.getInstance();
        var stats  = mc.player.getData(ZenkaiDataAttachments.PLAYER_STATS.get());
        var visual = mc.player.getData(ZenkaiDataAttachments.PLAYER_VISUAL.get());
        this.race     = stats.getRace();
        eyeColor      = visual.getEyeColorRgb()  | 0xFF000000;
        eyeRightColor = visual.getEyeColorRightRgb();
        hairColor     = visual.getHairColorRgb() | 0xFF000000;
        if (stats.getRace() == Race.MAJIN) hairColor = visual.getSkinColorRgb() | 0xFF000000; // el pelo del Majin = su piel
        hairStrandColors = visual.isHairStrandColors();
        vanillaSkin   = !visual.shouldRenderRaceSkin();

        // Si venimos de otra raza (o de un save viejo) el rasgo guardado puede no ser válido:
        // se corrige en el preview ya, y llega al servidor al salir (UpdatePlayerVisualPacket).
        FaceBans.reconcile(race, visual);
        faceParts.clear();
        for (FaceCategory c : FaceCategory.values()) {
            ResourceLocation id = visual.getFacePart(c);
            if (id != null) faceParts.put(c, id);
        }
        // Después de reconcile (que también corrige el pelo): ya es válido para la raza.
        hairSel = visual.getHairStyleId();

        // Desde el hub se arranca en la primera pestaña (el flujo es lineal: Next recorre todas);
        // desde Back del cuerpo, en la última.
        List<Tab> tabs = navigableTabs();
        tab = startAtLastTab ? tabs.get(tabs.size() - 1) : tabs.get(0);
        lastTab = tab;
        page = pageOfSelection();
    }

    /** Antes Human/Saiyan a fuego; ahora quien tenga algún peinado elegible en el catálogo. */
    private boolean hasHair() { return !HairCatalog.options(race).isEmpty(); }

    /** Cejas solo si el catálogo trae alguna para esta raza; pelo solo Human/Saiyan. */
    private boolean isTabVisible(Tab t) {
        if (hairOnly) return t == Tab.HAIR;
        if (t == Tab.HAIR) return hasHair();
        if (t == Tab.EYEBROWS) return !FaceCatalog.options(FaceCategory.EYEBROWS, race).isEmpty();
        return true;
    }

    private List<Tab> visibleTabs() {
        List<Tab> out = new ArrayList<>();
        for (Tab t : Tab.values()) if (isTabVisible(t)) out.add(t);
        return out;
    }

    /** Con la skin predeterminada solo el pelo es editable; las pestañas de cara quedan bloqueadas. */
    private boolean isTabLocked(Tab t) { return vanillaSkin && t != Tab.HAIR; }

    /** Pestañas por las que recorren Back/Next y en las que se puede entrar: las visibles y no bloqueadas. */
    private List<Tab> navigableTabs() {
        List<Tab> out = new ArrayList<>();
        for (Tab t : visibleTabs()) if (!isTabLocked(t)) out.add(t);
        return out.isEmpty() ? visibleTabs() : out;  // red de seguridad: nunca una lista vacía
    }

    /**
     * Opciones de la pestaña actual, en ORDEN ALFABÉTICO del nombre que ve el jugador (pedido del
     * usuario, 2026-10-01): "Ninguno" primero si la categoría lo admite, después el resto por
     * nombre traducido al idioma del juego, sin distinguir mayúsculas ni acentos (Collator en
     * fuerza PRIMARY: "Ancho" junto a "Árbol"). El "sort" del JSON queda como desempate.
     */
    private List<ResourceLocation> choices() {
        if (tab.category == null) return List.of();
        List<ResourceLocation> raw = FaceBans.choices(race, tab.category);
        List<ResourceLocation> out = new ArrayList<>();
        List<ResourceLocation> named = new ArrayList<>();
        for (ResourceLocation id : raw) {
            if (id == null) out.add(null); else named.add(id);
        }
        // Favoritos (clic derecho, Favorites) delante de todo, también de "Ninguno"; cada grupo
        // en orden alfabético.
        String cat = tab.category.key();
        Favorites.sort(named, cat, ResourceLocation::toString, id -> nameOf(id).getString());
        List<ResourceLocation> favs = new ArrayList<>();
        for (ResourceLocation id : named) if (Favorites.is(cat, id.toString())) favs.add(id);
        named.removeAll(favs);
        out.addAll(0, favs);
        out.addAll(named);
        return out;
    }

    private int pageCount() { return Math.max(1, (choices().size() + PER_PAGE - 1) / PER_PAGE); }

    private int pageOfSelection() {
        if (tab.category == null) return 0;
        int idx = choices().indexOf(faceParts.get(tab.category));
        return idx < 0 ? 0 : idx / PER_PAGE;
    }

    // ── Widgets ──────────────────────────────────────────────────────────────

    @Override
    protected void init() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (!stateLoaded) { loadState(); stateLoaded = true; }

        // clearWidgets() se lleva por delante un picker abierto sin avisar (gui.md, lección de
        // BodyColorsScreen): cerrarlo de forma explícita para no dejar el campo colgando.
        picker = null;
        activeChannel = null;
        this.clearWidgets();
        this.panelLeft = (this.width  - BG_W) / 2;
        this.panelTop  = (this.height - BG_H) / 2;
        int pl = panelLeft, pt = panelTop;

        buildTabs(pl, pt);
        // ANTES de la biblioteca: Borrar/Exportar se colocan según haya o no casilla de colores
        // (calcularlo después, en buildColorRows, dejaba la casilla encima de los botones).
        hairHasColors = tab == Tab.HAIR && !importOpen && selectedHairHasColors();
        if (tab == Tab.HAIR) buildHairLibrary(pl, pt);
        else buildGrid(pl, pt);
        buildColorRows(pl, pt);
        buildViewToggle(pl, pt);

        var resetViewBtn = new AtlasIconButton(pl + PV_X2 - 18, pt + PV_Y1 + 2,
                ICON_RESET_VIEW_U, ICON_RESET_VIEW_V, () -> zoomMult = 1.0f);
        resetViewBtn.setTooltip(Tooltip.create(Component.translatable("screen.zenkai.appearance.reset_view")));
        addRenderableWidget(resetViewBtn);

        addRenderableWidget(new TextOnlyButton(
                pl + IN_X1, pt + BTN_BAR_Y, BTN_W, 20,
                Component.translatable(hairOnly ? "screen.zenkai.cancel" : "screen.zenkai.back"),
                TEX_BTN, null,
                hairOnly ? this::onClose : this::goBack));

        addRenderableWidget(new TextOnlyButton(
                pl + IN_X2 - BTN_W, pt + BTN_BAR_Y, BTN_W, 20,
                Component.translatable(hairOnly ? "screen.zenkai.confirm" : "screen.zenkai.next"),
                TEX_BTN, null,
                hairOnly ? this::confirmHairOnly : this::goToStyle));
    }

    private void buildTabs(int pl, int pt) {
        List<Tab> tabs = visibleTabs();
        int x0 = pl + IN_X1 + PAD;
        int total = (IN_X2 - PAD) - (IN_X1 + PAD);
        int w = total / tabs.size();
        lockedTabRects.clear();
        for (int i = 0; i < tabs.size(); i++) {
            Tab t = tabs.get(i);
            var btn = new TextOnlyButton(x0 + i * w, pt + TAB_Y, w, TAB_H,
                    Component.translatable(t.key()), () -> switchTab(t))
                    .textColors(COLOR_TITLE, COLOR_VALUE, COLOR_MUTED).noShadow()
                    .selected(t == tab);
            if (isTabLocked(t)) {
                // active=false: no responde al clic, pero AbstractWidget sigue calculando el hover
                // y enseñando el tooltip, que es justo el "por qué" que se quiere dar.
                btn.active = false;
                btn.setTooltip(Tooltip.create(Component.translatable("screen.zenkai.head.locked_vanilla_skin")));
                lockedTabRects.add(new int[]{x0 + i * w, pt + TAB_Y, w, TAB_H});
            }
            addRenderableWidget(btn);
        }
    }

    private void buildGrid(int pl, int pt) {
        FaceCategory cat = tab.category;
        List<ResourceLocation> options = choices();
        page = Mth.clamp(page, 0, pageCount() - 1);
        int from = page * PER_PAGE;
        for (int i = from; i < Math.min(options.size(), from + PER_PAGE); i++) {
            ResourceLocation id = options.get(i);
            int slot = i - from;
            int x = pl + LEFT_X + (slot % GRID_COLS) * (FacePartTile.SIZE + GRID_GAP);
            int y = pt + GRID_Y + (slot / GRID_COLS) * (FacePartTile.SIZE + GRID_GAP);
            Component name = nameOf(id);
            var tile = new FacePartTile(x, y, cat, id, name,
                    () -> java.util.Objects.equals(faceParts.get(cat), id),
                    () -> selectPart(cat, id));
            tile.setTooltip(Tooltip.create(id == null ? name : name.copy().append("\n")
                    .append(Component.translatable("screen.zenkai.head.favorite_hint").withStyle(net.minecraft.ChatFormatting.GRAY))));
            if (id != null) {
                String key = id.toString();
                tile.favorite(() -> Favorites.is(cat.key(), key), () -> {
                    Favorites.toggle(cat.key(), key);
                    rebuildWidgets(); // el orden cambia
                });
            }
            addRenderableWidget(tile);
        }
        if (pageCount() > 1) {
            addRenderableWidget(new ArrowIconButton(pl + LEFT_X, pt + NAME_Y - 2,
                    ArrowIconButton.Dir.LEFT, () -> { page = Math.floorMod(page - 1, pageCount()); rebuildWidgets(); }));
            addRenderableWidget(new ArrowIconButton(pl + LEFT_R - ARROW_W, pt + NAME_Y - 2,
                    ArrowIconButton.Dir.RIGHT, () -> { page = Math.floorMod(page + 1, pageCount()); rebuildWidgets(); }));
        }
    }

    // ── Biblioteca de pelo ─────────────────────────────────────────────────────

    /**
     * Pelo: cuadrícula de 3×3 con miniatura 3D de tu cabeza con cada peinado (HairTile), igual que
     * ojos/boca/nariz, y carpetas (petición del usuario, 2026-10-12). Raíz: favoritos (de cualquier
     * carpeta), calvo, los peinados sin carpeta, las carpetas y al final "+" (Custom). Dentro de
     * una carpeta: volver y sus peinados. En Custom: volver, "+" para importar y los importados.
     * Clic derecho en un peinado = favorito (Favorites), que sale siempre primero.
     */
    private void buildHairLibrary(int pl, int pt) {
        if (importOpen) {
            buildImportPanel(pl, pt);
            return;
        }
        List<HairItem> items = hairItems();
        int pages = Math.max(1, (items.size() + PER_PAGE - 1) / PER_PAGE);
        hairPage = Mth.clamp(hairPage, 0, pages - 1);
        int from = hairPage * PER_PAGE;
        for (int i = from; i < Math.min(items.size(), from + PER_PAGE); i++) {
            HairItem it = items.get(i);
            int slot = i - from;
            int x = pl + LEFT_X + (slot % GRID_COLS) * (HairTile.SIZE + GRID_GAP);
            int y = pt + GRID_Y + (slot / GRID_COLS) * (HairTile.SIZE + GRID_GAP);
            HairTile tile = switch (it.kind()) {
                case STYLE -> {
                    String id = it.styleId();
                    HairTile t = HairTile.style(x, y, id, it.name(), () -> id.equals(hairSel), () -> selectHair(id));
                    if (!PlayerVisualAttachment.HAIR_NONE.equals(id)) {
                        t.favorite(() -> Favorites.is(HAIR_FAV, id), () -> {
                            Favorites.toggle(HAIR_FAV, id);
                            rebuildWidgets(); // el orden cambia
                        });
                    }
                    yield t;
                }
                case FOLDER -> HairTile.folder(x, y, HairStyleDef.folderIcon(it.folder()), it.name(),
                        () -> openHairFolder(it.folder().toString()));
                case PLUS -> HairTile.special(x, y, HairTile.Kind.PLUS, it.name(),
                        CUSTOM_FOLDER.equals(hairFolder) ? this::openImport : () -> openHairFolder(CUSTOM_FOLDER));
                case BACK -> HairTile.special(x, y, HairTile.Kind.BACK, it.name(), () -> openHairFolder(null));
            };
            boolean styleTile = it.kind() == HairTile.Kind.STYLE && !PlayerVisualAttachment.HAIR_NONE.equals(it.styleId());
            tile.setTooltip(Tooltip.create(styleTile
                    ? it.name().copy().append("\n").append(Component.translatable("screen.zenkai.head.favorite_hint")
                            .withStyle(net.minecraft.ChatFormatting.GRAY))
                    : it.name()));
            addRenderableWidget(tile);
        }
        if (pages > 1) {
            addRenderableWidget(new ArrowIconButton(pl + LEFT_X, pt + NAME_Y - 2, ArrowIconButton.Dir.LEFT,
                    () -> { hairPage = Math.floorMod(hairPage - 1, pages); rebuildWidgets(); }));
            addRenderableWidget(new ArrowIconButton(pl + LEFT_R - ARROW_W, pt + NAME_Y - 2, ArrowIconButton.Dir.RIGHT,
                    () -> { hairPage = Math.floorMod(hairPage + 1, pages); rebuildWidgets(); }));
        }
        // Borrar / Exportar: solo con un importado elegido, bajo el color de pelo (y bajo la
        // casilla de "Colores del código" si el peinado trae colores).
        if (selectedCustom() != null) {
            int half = GRID_W / 2, y = pt + ROW1_Y + ROW_H + (hairHasColors ? 15 : 2);
            addRenderableWidget(new TextOnlyButton(pl + LEFT_X, y, half, 12,
                    Component.translatable("screen.zenkai.head.delete"), this::deleteHair)
                    .textColors(COLOR_TITLE, COLOR_VALUE, COLOR_MUTED).noShadow());
            var export = new TextOnlyButton(pl + LEFT_X + half, y, GRID_W - half, 12,
                    Component.translatable("screen.zenkai.head.export"), this::exportHair)
                    .textColors(COLOR_TITLE, COLOR_VALUE, COLOR_MUTED).noShadow();
            export.setTooltip(Tooltip.create(Component.translatable("screen.zenkai.head.export_tooltip")));
            addRenderableWidget(export);
        }
    }

    /** Las casillas de la carpeta abierta, en orden (favoritos primero, luego alfabético). */
    private List<HairItem> hairItems() {
        List<HairItem> out = new ArrayList<>();
        Minecraft mc = Minecraft.getInstance();
        List<CustomHairLibrary.Entry> customs = mc.player == null || !ServerConfig.allowCustomHair() || !hasHair()
                ? List.of() : mc.player.getData(ZenkaiDataAttachments.PLAYER_VISUAL.get()).getCustomHairs();
        if (hairFolder == null) {
            List<HairItem> favs = new ArrayList<>(), rest = new ArrayList<>();
            java.util.Map<ResourceLocation, HairItem> folders = new java.util.LinkedHashMap<>();
            for (HairStyleDef d : HairCatalog.options(race)) {
                HairItem it = styleItem(d.id().toString(), d.displayName());
                if (Favorites.is(HAIR_FAV, it.styleId())) favs.add(it);
                else if (d.folder() == null) rest.add(it);
                if (d.folder() != null) {
                    folders.putIfAbsent(d.folder(), new HairItem(HairTile.Kind.FOLDER, null, d.folder(),
                            HairStyleDef.folderName(d.folder())));
                }
            }
            for (CustomHairLibrary.Entry e : customs) {
                if (Favorites.is(HAIR_FAV, e.styleId())) favs.add(styleItem(e.styleId(), Component.literal(e.name())));
            }
            sortHair(favs);
            sortHair(rest);
            List<HairItem> folderList = new ArrayList<>(folders.values());
            sortHair(folderList);
            out.addAll(favs);
            out.add(styleItem(PlayerVisualAttachment.HAIR_NONE, Component.translatable("screen.zenkai.head.none")));
            out.addAll(rest);
            out.addAll(folderList);
            if (ServerConfig.allowCustomHair() && hasHair()) {
                out.add(new HairItem(HairTile.Kind.PLUS, null, null, Component.translatable("screen.zenkai.head.custom_folder")));
            }
            return out;
        }
        out.add(new HairItem(HairTile.Kind.BACK, null, null, Component.translatable("screen.zenkai.head.back")));
        List<HairItem> inside = new ArrayList<>();
        if (CUSTOM_FOLDER.equals(hairFolder)) {
            out.add(new HairItem(HairTile.Kind.PLUS, null, null, Component.translatable("screen.zenkai.head.import_new")));
            for (CustomHairLibrary.Entry e : customs) inside.add(styleItem(e.styleId(), Component.literal(e.name())));
        } else {
            for (HairStyleDef d : HairCatalog.options(race)) {
                if (d.folder() != null && d.folder().toString().equals(hairFolder)) {
                    inside.add(styleItem(d.id().toString(), d.displayName()));
                }
            }
        }
        sortHair(inside);
        out.addAll(inside);
        return out;
    }

    private static HairItem styleItem(String id, Component name) {
        return new HairItem(HairTile.Kind.STYLE, id, null, name);
    }

    private static void sortHair(List<HairItem> items) {
        Favorites.sort(items, HAIR_FAV, it -> it.styleId() == null ? "" : it.styleId(), it -> it.name().getString());
    }

    private void openHairFolder(@Nullable String folder) {
        hairFolder = folder;
        hairPage = 0;
        hairStatus = null;
        rebuildWidgets();
    }

    private void openImport() {
        importOpen = true;
        hairStatus = null;
        rebuildWidgets();
    }

    private void selectHair(String id) {
        hairSel = id;
        hairStatus = null;
        applyPreview();
        rebuildWidgets(); // Borrar/Exportar dependen de si el elegido es importado
    }

    /**
     * Panel de importar (carpeta Custom, "+"): el código se PEGA aquí (Ctrl+V). No es un comando a
     * propósito: el chat corta a 256 caracteres y un código mide hasta 30 KB. Nombre opcional.
     * La casilla admite más que el tope de la biblioteca a propósito: un EditBox recorta lo pegado
     * sin avisar, y un código recortado se lee como "dañado"; así llega entero y el error es
     * "demasiado largo".
     */
    private void buildImportPanel(int pl, int pt) {
        int x = pl + LEFT_X, y = pt + GRID_Y;
        EditBox code = new EditBox(this.font, x, y + 11, GRID_W, 14, Component.translatable("screen.zenkai.head.code"));
        code.setMaxLength(DmzHairCodec.MAX_CODE_LENGTH);
        code.setHint(Component.translatable("screen.zenkai.head.import_hint").withStyle(st -> st.withColor(0x8A8A8A)));
        code.setValue(importDraft);
        code.setResponder(v -> importDraft = v);
        addRenderableWidget(code);
        EditBox name = new EditBox(this.font, x, y + 41, GRID_W, 14, Component.translatable("screen.zenkai.head.name"));
        name.setMaxLength(CustomHairLibrary.MAX_NAME_LENGTH);
        name.setHint(Component.translatable("screen.zenkai.head.name_hint").withStyle(st -> st.withColor(0x8A8A8A)));
        name.setValue(importName);
        name.setResponder(v -> importName = v);
        addRenderableWidget(name);
        int half = GRID_W / 2;
        addRenderableWidget(new TextOnlyButton(x, y + 61, half, 12,
                Component.translatable("screen.zenkai.head.import"), this::importHair)
                .textColors(COLOR_TITLE, COLOR_VALUE, COLOR_MUTED).noShadow());
        addRenderableWidget(new TextOnlyButton(x + half, y + 61, GRID_W - half, 12,
                Component.translatable("screen.zenkai.head.cancel"), () -> { importOpen = false; hairStatus = null; rebuildWidgets(); })
                .textColors(COLOR_TITLE, COLOR_VALUE, COLOR_MUTED).noShadow());
    }

    @Nullable
    private CustomHairLibrary.Entry selectedCustom() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return null;
        return mc.player.getData(ZenkaiDataAttachments.PLAYER_VISUAL.get())
                .findCustomHair(CustomHairLibrary.idFromStyle(hairSel));
    }

    /**
     * Valida el código aquí mismo (para avisar al momento, con el mismo lector que usa el
     * servidor), lo añade a la copia local y lo manda al servidor, que lo vuelve a validar y
     * responde con la biblioteca buena (CustomHairLibrarySyncPacket).
     */
    private void importHair() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        var visual = mc.player.getData(ZenkaiDataAttachments.PLAYER_VISUAL.get());
        CustomHairLibrary.Entry existing = importDraft.isBlank() ? null
                : visual.findCustomHair(CustomHairLibrary.idOf(importDraft));
        if (existing != null) {
            hairSel = existing.styleId();
            setHairStatus(Component.translatable("zenkai.hair_code.error.duplicate", existing.name()), false);
        } else {
            try {
                CustomHairLibrary.Entry e = CustomHairLibraryPacket.tryAdd(visual, race, true, importName, importDraft);
                PacketDistributor.sendToServer(CustomHairLibraryPacket.add(e.name(), e.code()));
                hairSel = e.styleId();
                importDraft = "";
                importName = "";
                setHairStatus(Component.translatable("zenkai.hair_library.added", e.name()), false);
            } catch (HairCodeException ex) {
                setHairStatus(ex.component(), true);
                rebuildWidgets();
                return;
            }
        }
        importOpen = false;
        hairFolder = CUSTOM_FOLDER;
        applyPreview();
        rebuildWidgets();
    }

    private void deleteHair() {
        Minecraft mc = Minecraft.getInstance();
        CustomHairLibrary.Entry e = selectedCustom();
        if (mc.player == null || e == null) return;
        var visual = mc.player.getData(ZenkaiDataAttachments.PLAYER_VISUAL.get());
        visual.setHairStyleId(hairSel);
        CustomHairLibraryPacket.remove(visual, race, e.id());
        PacketDistributor.sendToServer(CustomHairLibraryPacket.remove(e.id()));
        hairSel = visual.getHairStyleId();
        setHairStatus(Component.translatable("zenkai.hair_library.deleted", e.name()), false);
        applyPreview();
        rebuildWidgets();
    }

    /**
     * Para autores de modpacks: escribe el peinado como carpeta de hair_packs (pack de recursos +
     * datos que el mod carga solo), así entra en el CATÁLOGO del modpack para cualquier jugador.
     */
    private void exportHair() {
        CustomHairLibrary.Entry e = selectedCustom();
        if (e == null) return;
        try {
            var report = HairPackWriter.write(HairPackSource.root(), e.name(), HairCodes.decode(e.code()), true);
            setHairStatus(Component.translatable("zenkai.hair_library.exported",
                    report.folder().getFileName().toString()), false);
        } catch (HairCodeException ex) {
            setHairStatus(ex.component(), true);
        } catch (java.io.IOException ex) {
            setHairStatus(Component.translatable("zenkai.hair_library.export_error", ex.getMessage()), true);
        }
    }

    private void setHairStatus(Component c, boolean error) {
        hairStatus = c;
        hairStatusError = error;
    }

    /** Fila(s) de color según la pestaña: ojos (izq. + heterocromía), pelo; el resto es informativo. */
    private void buildColorRows(int pl, int pt) {
        int boxX = pl + LEFT_R - COLOR_BOX_W;
        if (tab == Tab.EYES) {
            addColorBox(boxX, pt + ROW1_Y, ColorChannel.EYE);
            boolean hetero = eyeRightColor >= 0;
            // Ancho = casilla + texto + el mismo margen a la derecha: TextOnlyButton centra el
            // texto, así queda justo detrás de la casilla que pinta renderColorLabels. Sin la
            // casilla se leía como una etiqueta más, no como algo que se pulsa (mockup S3).
            Component label = Component.translatable("screen.zenkai.head.heterochromia");
            int w = CHECK_W * 2 + this.font.width(label);
            addRenderableWidget(new TextOnlyButton(pl + LEFT_X, pt + ROW1_Y + ROW_H - 1, w, 12,
                    label, this::toggleHeterochromia)
                    .textColors(COLOR_TITLE, COLOR_VALUE, COLOR_MUTED).noShadow());
            if (hetero) addColorBox(boxX, pt + ROW1_Y + ROW_H, ColorChannel.EYE_RIGHT);
        } else if (tab == Tab.HAIR) {
            if (race != Race.MAJIN) addColorBox(boxX, pt + ROW1_Y, ColorChannel.HAIR);
            if (hairHasColors) {
                // Mismo patrón que la heterocromía: casilla pintada en renderColorLabels + texto.
                Component label = Component.translatable("screen.zenkai.head.strand_colors");
                var b = new TextOnlyButton(pl + LEFT_X, pt + ROW1_Y + ROW_H - 1, CHECK_W * 2 + this.font.width(label), 12,
                        label, this::toggleStrandColors)
                        .textColors(COLOR_TITLE, COLOR_VALUE, COLOR_MUTED).noShadow();
                b.setTooltip(Tooltip.create(Component.translatable("screen.zenkai.head.strand_colors_tooltip")));
                addRenderableWidget(b);
            }
        }
    }

    /**
     * ¿El peinado elegido (importado o del catálogo por código) trae colores propios en algún
     * mechón? Sin ellos la casilla no significaría nada y no se enseña.
     */
    private boolean selectedHairHasColors() {
        String code = null;
        CustomHairLibrary.Entry custom = selectedCustom();
        if (custom != null) {
            code = custom.code();
        } else if (hairSel != null && !PlayerVisualAttachment.HAIR_NONE.equals(hairSel)) {
            HairStyleDef style = com.hmc.zenkai.feature.race.hair.HairCatalog.get(ResourceLocation.tryParse(hairSel));
            if (style != null && style.representation() == HairStyleDef.Representation.CODE) code = style.code();
        }
        return code != null && com.hmc.zenkai.feature.race.hair.strand.CustomHairCodes.hasColors(code);
    }

    private void toggleStrandColors() {
        hairStrandColors = !hairStrandColors;
        applyPreview();
        rebuildWidgets();
    }

    private void addColorBox(int x, int y, ColorChannel ch) {
        addRenderableWidget(new ColorBoxButton(x, y, COLOR_BOX_W, COLOR_BOX_H,
                () -> colorForChannel(ch) & 0xFFFFFF,
                () -> activeChannel == ch,
                () -> togglePicker(ch)));
    }

    private void buildViewToggle(int pl, int pt) {
        int half = (LEFT_R - LEFT_X) / 2;
        addRenderableWidget(new TextOnlyButton(pl + LEFT_X, pt + VIEW_Y, half, 12,
                Component.translatable("screen.zenkai.head.view_face"),
                () -> { faceView = true; zoomMult = 1.0f; rebuildWidgets(); })
                .textColors(COLOR_TITLE, COLOR_VALUE, COLOR_MUTED).noShadow().selected(faceView));
        addRenderableWidget(new TextOnlyButton(pl + LEFT_X + half, pt + VIEW_Y, half, 12,
                Component.translatable("screen.zenkai.head.view_body"),
                () -> { faceView = false; zoomMult = 1.0f; rebuildWidgets(); })
                .textColors(COLOR_TITLE, COLOR_VALUE, COLOR_MUTED).noShadow().selected(!faceView));
    }

    // ── Acciones ─────────────────────────────────────────────────────────────

    private void switchTab(Tab t) {
        if (t == tab) return;
        tab = t;
        lastTab = t;
        page = pageOfSelection();
        rebuildWidgets();
    }

    private void selectPart(FaceCategory category, @Nullable ResourceLocation id) {
        if (id == null) faceParts.remove(category); else faceParts.put(category, id);
        applyPreview();
    }

    private void toggleHeterochromia() {
        // Al activarla el derecho arranca igual que el izquierdo: nada cambia hasta que se elija.
        eyeRightColor = eyeRightColor >= 0 ? -1 : (eyeColor & 0xFFFFFF);
        applyPreview();
        rebuildWidgets();
    }

    // ── Color pickers ────────────────────────────────────────────────────────

    private void togglePicker(ColorChannel ch) {
        if (activeChannel == ch && picker != null) { closePicker(); return; }
        openPicker(ch);
    }

    private void openPicker(ColorChannel channel) {
        closePicker();
        activeChannel = channel;
        String label = Component.translatable(switch (channel) {
            case EYE       -> eyeRightColor >= 0 ? "screen.zenkai.head.left_eye_color" : "screen.zenkai.head.eye_color";
            case EYE_RIGHT -> "screen.zenkai.head.right_eye_color";
            case HAIR      -> "screen.zenkai.head.hair_color";
        }).getString();
        int pickerX = panelLeft + BG_W + 8;
        if (pickerX + ColorPickerWidget.TOTAL_W > this.width - 4)
            pickerX = panelLeft - ColorPickerWidget.TOTAL_W - 8;
        picker = new ColorPickerWidget(pickerX, panelTop + IN_Y1, colorForChannel(channel), label, argb -> {
            switch (channel) {
                case EYE       -> eyeColor = argb;
                case EYE_RIGHT -> eyeRightColor = argb & 0xFFFFFF;
                case HAIR      -> hairColor = argb;
            }
            applyPreview();
        });
        addRenderableWidget(picker);
    }

    private void closePicker() {
        if (picker != null) { removeWidget(picker); picker = null; }
        activeChannel = null;
    }

    private int colorForChannel(ColorChannel ch) {
        return switch (ch) {
            case EYE       -> eyeColor;
            case EYE_RIGHT -> 0xFF000000 | (eyeRightColor >= 0 ? eyeRightColor : eyeColor);
            case HAIR      -> hairColor;
        };
    }

    // ── Render ───────────────────────────────────────────────────────────────

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        int pl = panelLeft, pt = panelTop;

        super.renderBackground(g, mouseX, mouseY, partialTick);
        g.blit(BG, pl, pt, 0, 0, BG_W, BG_H);
        ScreenTitle.drawAbovePanel(g, mc.font, this.title, pl + BG_W / 2, pt);

        g.fill(pl + IN_X1 + PAD, pt + SEP1_Y, pl + IN_X2 - PAD, pt + SEP1_Y + 1, ZenkaiPalette.SEPARATOR);
        // Pestañas bloqueadas: velo oscuro DEBAJO del texto (los widgets se pintan en super.render).
        for (int[] r : lockedTabRects) g.fill(r[0], r[1], r[0] + r[2], r[1] + r[3], 0x55000000);
        g.fill(pl + LEFT_X, pt + SEP2_Y, pl + LEFT_R, pt + SEP2_Y + 1, ZenkaiPalette.SEPARATOR);

        int leftCx = pl + LEFT_X + GRID_W / 2;
        if (tab == Tab.HAIR) {
            if (importOpen) {
                g.drawString(this.font, Component.translatable("screen.zenkai.head.code"), pl + LEFT_X, pt + GRID_Y + 1, COLOR_TITLE, false);
                g.drawString(this.font, Component.translatable("screen.zenkai.head.name"), pl + LEFT_X, pt + GRID_Y + 31, COLOR_TITLE, false);
            } else if (!(hairStatus != null && hairHasColors && selectedCustom() != null)) {
                text(g, fitBetweenArrows(hairName()), leftCx, pt + NAME_Y, COLOR_VALUE);
            }
            if (hairStatus != null && hairHasColors && selectedCustom() != null && !importOpen) {
                // Sin hueco abajo (casilla de colores + Borrar/Exportar): el aviso va en la línea
                // del nombre, que sigue en el tooltip de la casilla.
                text(g, fitBetweenArrows(hairStatus), leftCx, pt + NAME_Y, hairStatusError ? 0xFFB03A2E : COLOR_VALUE);
            } else if (hairStatus != null) {
                // Hasta dos líneas al ancho de la columna: en el panel de importar, bajo sus botones;
                // si no, en el hueco de debajo del color de pelo (sin pisar Borrar/Exportar).
                var lines = this.font.split(hairStatus, GRID_W);
                int y = importOpen ? pt + GRID_Y + 78 : pt + ROW1_Y + ROW_H + 16;
                int max = importOpen ? 2 : 1;
                for (int i = 0; i < Math.min(max, lines.size()); i++) {
                    g.drawString(this.font, lines.get(i), leftCx - this.font.width(lines.get(i)) / 2,
                            y + i * 10, hairStatusError ? 0xFFB03A2E : COLOR_VALUE, false);
                }
            }
        } else {
            Component name = nameOf(faceParts.get(tab.category));
            if (pageCount() > 1) name = Component.literal((page + 1) + "/" + pageCount() + "  ").append(name);
            text(g, fitBetweenArrows(name), leftCx, pt + NAME_Y, COLOR_VALUE);
            if (choices().isEmpty()) {
                text(g, Component.translatable("screen.zenkai.head.no_options"), leftCx, pt + GRID_Y + GRID_H / 2 - 4, COLOR_MUTED);
            }
        }
        renderColorLabels(g, pl, pt);

        // Preview: marco + jugador. Cara = vista frontal fija; cuerpo = sigue al ratón como antes.
        // Borde de la placa de TP/MND del skill tree (2 px), por FUERA del área del render.
        NineSlice.plateBorder(g, pl + PV_X1 - 2, pt + PV_Y1 - 2, PV_X2 - PV_X1 + 4, PV_Y2 - PV_Y1 + 4);
        if (faceView) {
            int size = Math.round(FACE_SIZE * zoomMult);
            InventoryScreen.renderEntityInInventoryFollowsAngle(g, pl + PV_X1, pt + PV_Y1, pl + PV_X2, pt + PV_Y2,
                    size, FACE_Y_OFFSET, 0f, 0f, mc.player);
        } else {
            int size = Math.round((PV_Y2 - PV_Y1) * BODY_SIZE_RATIO * zoomMult);
            InventoryScreen.renderEntityInInventoryFollowsMouse(g, pl + PV_X1, pt + PV_Y1, pl + PV_X2, pt + PV_Y2,
                    size, BODY_Y_OFFSET, (float) mouseX, (float) mouseY, mc.player);
        }

        super.render(g, mouseX, mouseY, partialTick);
    }

    private void renderColorLabels(GuiGraphics g, int pl, int pt) {
        var font = Minecraft.getInstance().font;
        int x = pl + LEFT_X;
        switch (tab) {
            case EYES -> {
                g.drawString(font, Component.translatable(eyeRightColor >= 0
                        ? "screen.zenkai.head.left_eye_color" : "screen.zenkai.head.eye_color"),
                        x, pt + ROW1_Y + 1, COLOR_TITLE, false);
                // Casilla de la heterocromía (el botón de texto va justo a su derecha).
                int cx = x + 1, cy = pt + ROW1_Y + ROW_H + 1;
                g.fill(cx, cy, cx + CHECK_W - 2, cy + CHECK_W - 2, COLOR_TITLE);
                g.fill(cx + 1, cy + 1, cx + CHECK_W - 3, cy + CHECK_W - 3, ZenkaiPalette.BEIGE);
                if (eyeRightColor >= 0) g.fill(cx + 2, cy + 2, cx + CHECK_W - 4, cy + CHECK_W - 4, COLOR_VALUE);
            }
            case HAIR -> {
                if (race == Race.MAJIN) {
                    g.drawString(font, Component.translatable("screen.zenkai.head.color_follows_skin"),
                            x, pt + ROW1_Y + 1, COLOR_MUTED, false);
                } else {
                    g.drawString(font, Component.translatable("screen.zenkai.head.hair_color"),
                            x, pt + ROW1_Y + 1, COLOR_TITLE, false);
                }
                if (hairHasColors && !importOpen) {
                    int cx = x + 1, cy = pt + ROW1_Y + ROW_H + 1;
                    g.fill(cx, cy, cx + CHECK_W - 2, cy + CHECK_W - 2, COLOR_TITLE);
                    g.fill(cx + 1, cy + 1, cx + CHECK_W - 3, cy + CHECK_W - 3, ZenkaiPalette.BEIGE);
                    if (hairStrandColors) g.fill(cx + 2, cy + 2, cx + CHECK_W - 4, cy + CHECK_W - 4, COLOR_VALUE);
                }
            }
            case EYEBROWS -> g.drawString(font, Component.translatable("screen.zenkai.head.color_follows_hair"),
                    x, pt + ROW1_Y + 1, COLOR_MUTED, false);
            case NOSE, MOUTH -> g.drawString(font, Component.translatable("screen.zenkai.head.color_follows_skin"),
                    x, pt + ROW1_Y + 1, COLOR_MUTED, false);
        }
    }

    /**
     * El nombre bajo la cuadrícula va entre las flechas de página: si no cabe se recorta con "…"
     * (nombres largos como "Broly Super II (Heroes of Eden)" pisaban las flechas). Entero en el tooltip.
     */
    private Component fitBetweenArrows(Component c) {
        int max = GRID_W - 2 * ARROW_W - 6;
        String s = c.getString();
        if (this.font.width(s) <= max) return c;
        return Component.literal(this.font.plainSubstrByWidth(s, max - this.font.width("…")).stripTrailing() + "…");
    }

    private void text(GuiGraphics g, Component c, int cx, int y, int color) {
        PanelText.centeredOnPanel(g, Minecraft.getInstance().font, c, cx, y, color);
    }

    private Component hairName() {
        CustomHairLibrary.Entry custom = selectedCustom();
        if (custom != null) return Component.literal(custom.name());
        HairStyleDef def = HairCatalog.get(ResourceLocation.tryParse(hairSel));
        return def != null ? def.displayName() : Component.translatable("screen.zenkai.head.none");
    }

    private static Component nameOf(@Nullable ResourceLocation id) {
        FacePartDef def = FaceCatalog.get(id);
        return def != null ? def.displayName() : Component.translatable("screen.zenkai.head.none");
    }

    @Override public void renderBackground(@NotNull GuiGraphics g, int mx, int my, float pt) {}

    // ── Entrada ──────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (picker != null) {
            boolean inside = mx >= picker.getX() && mx < picker.getX() + ColorPickerWidget.TOTAL_W
                    && my >= picker.getY() && my < picker.getY() + ColorPickerWidget.TOTAL_H;
            if (!inside) closePicker();
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double dx, double dy) {
        int x1 = panelLeft + PV_X1, x2 = panelLeft + PV_X2, y1 = panelTop + PV_Y1, y2 = panelTop + PV_Y2;
        if (mouseX >= x1 && mouseX < x2 && mouseY >= y1 && mouseY < y2) {
            zoomMult = Mth.clamp(zoomMult + (dy > 0 ? ZOOM_STEP : -ZOOM_STEP), ZOOM_MIN, ZOOM_MAX);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, dx, dy);
    }

    private void applyPreview() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        var visual = mc.player.getData(ZenkaiDataAttachments.PLAYER_VISUAL.get());
        visual.setEyeColorRgb(eyeColor & 0xFFFFFF);
        visual.setEyeColorRightRgb(eyeRightColor);
        visual.setHairColorRgb(hairColor & 0xFFFFFF);
        visual.setHairStrandColors(hairStrandColors);
        for (FaceCategory c : FaceCategory.values()) visual.setFacePart(c, faceParts.get(c));
        visual.setHairStyleId(hairSel);
    }

    /** "Back": retrocede a la pestaña anterior visible y solo desde la primera vuelve al hub. */
    private void goBack() {
        List<Tab> tabs = navigableTabs();
        int i = tabs.indexOf(tab);
        if (i > 0) {
            switchTab(tabs.get(i - 1));
            return;
        }
        goingToHub = true;
        Minecraft.getInstance().setScreen(hub);
    }

    /**
     * "Next": avanza a la siguiente pestaña visible (ojos → nariz → boca → pelo) y solo desde la
     * última pasa a StyleSelectionScreen. Antes saltaba de una al estilo y se saltaba las demás
     * categorías sin que el jugador las viera.
     */
    private void goToStyle() {
        List<Tab> tabs = navigableTabs();
        int i = tabs.indexOf(tab);
        if (i >= 0 && i + 1 < tabs.size()) {
            switchTab(tabs.get(i + 1));
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        goingNext = true;
        mc.setScreen(new BodyColorsScreen(hub, statsSnapshot, visualSnapshot));
    }

    /** "Confirmar" de las tijeras: guarda el pelo y cierra (removed() ya no revierte). */
    private void confirmHairOnly() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        applyPreview();
        var visual = mc.player.getData(ZenkaiDataAttachments.PLAYER_VISUAL.get());
        // El servidor solo toma los campos cosméticos de la lista blanca (loadCosmeticFromClient)
        // y vuelve a comprobar el peinado para la raza (FaceBans.reconcile).
        PacketDistributor.sendToServer(com.hmc.zenkai.feature.race.UpdatePlayerVisualPacket.from(visual));
        goingNext = true;
        mc.setScreen(null);
    }

    @Override
    public void removed() {
        if (!goingToHub && !goingNext) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                var stats  = mc.player.getData(ZenkaiDataAttachments.PLAYER_STATS.get());
                var visual = mc.player.getData(ZenkaiDataAttachments.PLAYER_VISUAL.get());
                if (statsSnapshot  != null) stats.load(statsSnapshot);
                if (visualSnapshot != null) visual.load(visualSnapshot);
            }
        }
        super.removed();
    }

    @Override public boolean isPauseScreen() { return false; }
}
