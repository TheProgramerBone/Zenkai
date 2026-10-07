package com.hmc.zenkai.client.gui.screens;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.feature.race.hair.HairCatalog;
import com.hmc.zenkai.feature.race.hair.HairStyleDef;
import com.hmc.zenkai.client.gui.PanelText;
import com.hmc.zenkai.client.gui.ScreenTitle;
import com.hmc.zenkai.client.gui.ZenkaiPalette;
import com.hmc.zenkai.client.gui.buttons.ArrowIconButton;
import com.hmc.zenkai.client.gui.buttons.AtlasIconButton;
import com.hmc.zenkai.client.gui.buttons.TextOnlyButton;
import com.hmc.zenkai.client.gui.widgets.ColorBoxButton;
import com.hmc.zenkai.client.gui.widgets.ColorPickerWidget;
import com.hmc.zenkai.client.gui.widgets.FacePartTile;
import com.hmc.zenkai.feature.Race;
import com.hmc.zenkai.feature.race.FaceBans;
import com.hmc.zenkai.feature.race.face.FaceCatalog;
import com.hmc.zenkai.feature.race.face.FaceCategory;
import com.hmc.zenkai.feature.race.face.FacePartDef;
import com.hmc.zenkai.registry.ZenkaiDataAttachments;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

/**
 * Sección "Head" del hub de apariencia ({@link AppearanceScreen}): ojos, cejas, nariz, boca (del
 * catálogo FaceCatalog) y pelo (solo Human/Saiyan, todavía por índice hasta S5).
 *
 * REDISEÑO S3 (2026-10-01, .claude/pendiente/sistema-visual-personajes-diseno.md): antes eran
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
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/common_screen.png");
    private static final ResourceLocation TEX_BTN =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/btn_wide.png");

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

    private final AppearanceScreen hub;
    private final CompoundTag statsSnapshot;
    private final CompoundTag visualSnapshot;

    private boolean goingToHub = false, goingNext = false;
    private boolean stateLoaded = false;
    private int panelLeft, panelTop;
    private Race race = Race.HUMAN;

    private Tab tab = Tab.EYES;
    private int page = 0;

    /** Peinado elegido (id de HairCatalog), null = calvo. Un "custom:" (HairCode) se conserva aparte. */
    @Nullable private ResourceLocation hairStyle;
    /** true si el jugador lleva un código procedimental (HairCode): no se pisa hasta que toque las flechas. */
    private boolean keepCustomHair;
    /** Rasgo elegido por categoría (id del FaceCatalog); sin entrada = ninguno. */
    private final EnumMap<FaceCategory, ResourceLocation> faceParts = new EnumMap<>(FaceCategory.class);
    private int eyeColor = 0xFF2E86C1, hairColor = 0xFF1A1A1A;
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

    public HeadAppearanceScreen(AppearanceScreen hub,
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

        // Si venimos de otra raza (o de un save viejo) el rasgo guardado puede no ser válido:
        // se corrige en el preview ya, y llega al servidor al salir (UpdatePlayerVisualPacket).
        FaceBans.reconcile(race, visual);
        faceParts.clear();
        for (FaceCategory c : FaceCategory.values()) {
            ResourceLocation id = visual.getFacePart(c);
            if (id != null) faceParts.put(c, id);
        }
        // Después de reconcile (que también corrige el pelo): ya es válido para la raza.
        hairStyle = visual.getHairStyle();
        keepCustomHair = com.hmc.zenkai.feature.race.HairCode.isCustom(visual.getHairStyleId());

        // Desde el hub se arranca en la primera pestaña (el flujo es lineal: Next recorre todas);
        // desde Back del cuerpo, en la última.
        List<Tab> tabs = visibleTabs();
        tab = startAtLastTab ? tabs.get(tabs.size() - 1) : tabs.get(0);
        lastTab = tab;
        page = pageOfSelection();
    }

    /** Antes Human/Saiyan a fuego; ahora quien tenga algún peinado elegible en el catálogo. */
    private boolean hasHair() { return !HairCatalog.options(race).isEmpty(); }

    /** Cejas solo si el catálogo trae alguna para esta raza; pelo solo Human/Saiyan. */
    private boolean isTabVisible(Tab t) {
        if (t == Tab.HAIR) return hasHair();
        if (t == Tab.EYEBROWS) return !FaceCatalog.options(FaceCategory.EYEBROWS, race).isEmpty();
        return true;
    }

    private List<Tab> visibleTabs() {
        List<Tab> out = new ArrayList<>();
        for (Tab t : Tab.values()) if (isTabVisible(t)) out.add(t);
        return out;
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
        java.text.Collator collator = java.text.Collator.getInstance(java.util.Locale.ROOT);
        collator.setStrength(java.text.Collator.PRIMARY);
        named.sort((a, b) -> collator.compare(nameOf(a).getString(), nameOf(b).getString()));
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
        if (tab == Tab.HAIR) buildHairSelector(pl, pt);
        else buildGrid(pl, pt);
        buildColorRows(pl, pt);
        buildViewToggle(pl, pt);

        var resetViewBtn = new AtlasIconButton(pl + PV_X2 - 18, pt + PV_Y1 + 2,
                ICON_RESET_VIEW_U, ICON_RESET_VIEW_V, () -> zoomMult = 1.0f);
        resetViewBtn.setTooltip(Tooltip.create(Component.translatable("screen.zenkai.appearance.reset_view")));
        addRenderableWidget(resetViewBtn);

        addRenderableWidget(new TextOnlyButton(
                pl + IN_X1, pt + BTN_BAR_Y, BTN_W, 20,
                Component.translatable("screen.zenkai.back"),
                TEX_BTN, null,
                this::goBack));

        addRenderableWidget(new TextOnlyButton(
                pl + IN_X2 - BTN_W, pt + BTN_BAR_Y, BTN_W, 20,
                Component.translatable("screen.zenkai.next"),
                TEX_BTN, null,
                this::goToStyle));
    }

    private void buildTabs(int pl, int pt) {
        List<Tab> tabs = visibleTabs();
        int x0 = pl + IN_X1 + PAD;
        int total = (IN_X2 - PAD) - (IN_X1 + PAD);
        int w = total / tabs.size();
        for (int i = 0; i < tabs.size(); i++) {
            Tab t = tabs.get(i);
            addRenderableWidget(new TextOnlyButton(x0 + i * w, pt + TAB_Y, w, TAB_H,
                    Component.translatable(t.key()), () -> switchTab(t))
                    .textColors(COLOR_TITLE, COLOR_VALUE, COLOR_MUTED).noShadow()
                    .selected(t == tab));
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
            tile.setTooltip(Tooltip.create(name));
            addRenderableWidget(tile);
        }
        if (pageCount() > 1) {
            addRenderableWidget(new ArrowIconButton(pl + LEFT_X, pt + NAME_Y - 2,
                    ArrowIconButton.Dir.LEFT, () -> { page = Math.floorMod(page - 1, pageCount()); rebuildWidgets(); }));
            addRenderableWidget(new ArrowIconButton(pl + LEFT_R - ARROW_W, pt + NAME_Y - 2,
                    ArrowIconButton.Dir.RIGHT, () -> { page = Math.floorMod(page + 1, pageCount()); rebuildWidgets(); }));
        }
    }

    /**
     * Pelo: peinados del catálogo (HairCatalog, S5) con flechas + nombre. Sin miniaturas todavía:
     * un peinado 3D no cabe en una miniatura plana de la cara (haría falta un render 3D por
     * casilla); el preview grande con la cámara a la cara ya lo enseña.
     */
    private void buildHairSelector(int pl, int pt) {
        int y = pt + GRID_Y + GRID_H / 2 - 6;
        addRenderableWidget(new ArrowIconButton(pl + LEFT_X, y, ArrowIconButton.Dir.LEFT, () -> {
            hairStyle = HairCatalog.step(race, hairStyle, -1);
            keepCustomHair = false;
            applyPreview();
        }));
        addRenderableWidget(new ArrowIconButton(pl + LEFT_R - ARROW_W, y, ArrowIconButton.Dir.RIGHT, () -> {
            hairStyle = HairCatalog.step(race, hairStyle, +1);
            keepCustomHair = false;
            applyPreview();
        }));
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
            addColorBox(boxX, pt + ROW1_Y, ColorChannel.HAIR);
        }
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
        g.fill(pl + LEFT_X, pt + SEP2_Y, pl + LEFT_R, pt + SEP2_Y + 1, ZenkaiPalette.SEPARATOR);

        int leftCx = pl + LEFT_X + GRID_W / 2;
        if (tab == Tab.HAIR) {
            text(g, Component.translatable("screen.zenkai.head.style"), leftCx, pt + GRID_Y + GRID_H / 2 - 20, COLOR_TITLE);
            HairStyleDef hairDef = HairCatalog.get(hairStyle);
            Component hairName = keepCustomHair ? Component.literal("Custom")
                    : hairDef != null ? hairDef.displayName() : Component.translatable("screen.zenkai.head.none");
            text(g, hairName, leftCx, pt + GRID_Y + GRID_H / 2 - 4, COLOR_VALUE);
        } else {
            Component name = nameOf(faceParts.get(tab.category));
            if (pageCount() > 1) name = Component.literal((page + 1) + "/" + pageCount() + "  ").append(name);
            text(g, name, leftCx, pt + NAME_Y, COLOR_VALUE);
            if (choices().isEmpty()) {
                text(g, Component.translatable("screen.zenkai.head.no_options"), leftCx, pt + GRID_Y + GRID_H / 2 - 4, COLOR_MUTED);
            }
        }
        renderColorLabels(g, pl, pt);

        // Preview: marco + jugador. Cara = vista frontal fija; cuerpo = sigue al ratón como antes.
        g.fill(pl + PV_X1 - 1, pt + PV_Y1 - 1, pl + PV_X2 + 1, pt + PV_Y2 + 1, ZenkaiPalette.SEPARATOR);
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
            case HAIR -> g.drawString(font, Component.translatable("screen.zenkai.head.hair_color"),
                    x, pt + ROW1_Y + 1, COLOR_TITLE, false);
            case EYEBROWS -> g.drawString(font, Component.translatable("screen.zenkai.head.color_follows_hair"),
                    x, pt + ROW1_Y + 1, COLOR_MUTED, false);
            case NOSE, MOUTH -> g.drawString(font, Component.translatable("screen.zenkai.head.color_follows_skin"),
                    x, pt + ROW1_Y + 1, COLOR_MUTED, false);
        }
    }

    private void text(GuiGraphics g, Component c, int cx, int y, int color) {
        PanelText.centeredOnPanel(g, Minecraft.getInstance().font, c, cx, y, color);
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
        for (FaceCategory c : FaceCategory.values()) visual.setFacePart(c, faceParts.get(c));
        // Un código procedimental (HairCode) se respeta hasta que el jugador toque las flechas.
        if (!keepCustomHair) visual.setHairStyle(hairStyle);
    }

    /** "Back": retrocede a la pestaña anterior visible y solo desde la primera vuelve al hub. */
    private void goBack() {
        List<Tab> tabs = visibleTabs();
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
        List<Tab> tabs = visibleTabs();
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
