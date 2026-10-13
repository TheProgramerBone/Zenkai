package com.hmc.zenkai.client.gui.screens;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.gui.ScreenTitle;
import com.hmc.zenkai.client.gui.buttons.AtlasIconButton;
import com.hmc.zenkai.client.gui.buttons.TextOnlyButton;
import com.hmc.zenkai.client.gui.widgets.ColorBoxButton;
import com.hmc.zenkai.client.gui.widgets.ColorPickerWidget;
import com.hmc.zenkai.feature.Race;
import com.hmc.zenkai.feature.race.layer.GeoLayerArmorItem;
import com.hmc.zenkai.feature.race.layer.RaceLayerDiscovery;
import com.hmc.zenkai.feature.race.RaceSkinSlots;
import com.hmc.zenkai.registry.ZenkaiDataAttachments;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.client.Minecraft;
import com.hmc.zenkai.client.gui.PanelText;
import com.hmc.zenkai.client.gui.ZenkaiPalette;
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

/**
 * Sección "Body & Colors" del hub de apariencia ({@link AppearanceScreen}): tono de piel
 * (Human/Saiyan/Majin) o una fila de color por capa descubierta (Namek/Arcosian, ver
 * {@link RaceLayerDiscovery}). REDISEÑADA por completo respecto al antiguo modo BODY: las filas
 * de color ahora usan el ANCHO COMPLETO del panel arriba (antes compartían columna estrecha con
 * el preview) — el caso que forzó esto es Arcosiano (6 capas: Skin/Muscles/Armor/Armor
 * Detail/Armor Detail 2/Horns, 4 presets cada una) que antes se cortaba por debajo del panel.
 *
 * {@link RaceLayerDiscovery} no pone ningún tope al número de capas (es puramente
 * convención de archivo, `_layer_0.png`, `_layer_1.png`... hasta el primer hueco) — la zona de
 * filas está dimensionada para que las 6 filas de Arcosiano quepan HOY sin scroll, pero lleva
 * además una scrollbar de seguridad estilo MasterScreen (aparece solo si maxScroll() > 0) por si
 * una raza futura trae más capas. Human/Saiyan/Majin (0 filas de capa) hace que maxScroll()
 * sea 0 y la scrollbar simplemente no se dibuje, sin caso especial en el código de scroll.
 *
 * PREVIEW Y DIVISOR DINÁMICOS: un primer intento reservaba un hueco fijo y pequeño para el
 * preview (pensado para el peor caso, Arcosiano) — pero cualquier raza con menos contenido
 * (Namekiano con 3 filas, o la rejilla de tonos de Human/Saiyan/Majin) dejaba un hueco vacío
 * enorme SIN USAR entre el contenido y el preview, que además se veía siempre igual de
 * pequeño aunque sobrara espacio de sobra. contentBottom (ver buildLayerRows/
 * buildSkinToneGrid) guarda dónde termina el contenido REAL de la raza actual; el divisor se
 * dibuja justo debajo de eso, y el preview crece para llenar lo que quede hasta el borde
 * del panel — tamaño y ancho del preview se derivan del alto disponible (mismas proporciones
 * que ya usa HeadAppearanceScreen: ancho ≈ 0.78×alto, tamaño de render ≈ 0.44×alto), con un
 * tope para que no se vuelva absurdamente grande cuando casi no hay contenido.
 *
 * SINCRONIZACIÓN: mismo contrato que {@link HeadAppearanceScreen} — se crea siempre de cero
 * desde el hub, applyPreview() aquí solo escribe piel/capas (NO ojos/pelo/boca/nariz/género).
 * Un ÚNICO botón "Back" (el de la barra inferior, sin fila "‹ Back" propia arriba — ver
 * AppearanceScreen) vuelve al mismo hub que la creó, y removed() solo revierte si ningún flag
 * de salida intencional (goingToHub/goingNext) se marcó antes.
 */
public class BodyColorsScreen extends Screen {

    private static final ResourceLocation BG =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/common_screen.png");

    private static final ResourceLocation TEX_BTN =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/btn_wide.png");

    private static final int BG_W = 256;
    private static final int BG_H = 256;
    // Beige real de common_screen.png: x=12..243 (el marco ocupa 0..11 y 244..255). Antes era
    // 10/245, copiado de las pantallas viejas, y lo que va pegado al borde pisaba el marco. Ver gui.md.
    private static final int IN_X1 = 12;
    private static final int IN_Y1 = 10;
    private static final int IN_X2 = 243;
    private static final int IN_Y2 = 240;
    private static final int BTN_BAR_Y = 260;
    private static final int BTN_W     = 60;

    private static final int CONTENT_Y0 = IN_Y1 + 6;
    private static final int PAD        = 8;

    private static final int COLOR_BOX_W  = 12;
    private static final int COLOR_BOX_H  = 10;
    private static final int PRESET_BOX_W = 12;
    private static final int PRESET_BOX_H = 10;

    private static final int SCROLLBAR_W   = 4;
    private static final int SCROLLBAR_GAP = 6;

    // ── Franja de color, ancho completo (Namek/Arcosian: una fila por capa) ────
    // Sin fila "‹ Back" que reservar arriba (ver javadoc de la clase) — ROWS_TOP arranca
    // directo en CONTENT_Y0.
    private static final int ROWS_TOP = CONTENT_Y0;   // 16
    private static final int ROW_H    = 24;

    // ── Zona de filas: techo fijo, fondo fijo SOLO como tope de scroll (ver javadoc de la
    // clase) — 6 filas de Arcosiano (144px) caben con margen dentro de este máximo. ──
    private static final int ROWS_BOTTOM_MAX = ROWS_TOP + 160;   // 176

    // ── Preview: dinámico, ver javadoc de la clase — estas son solo las constantes de forma. ──
    private static final int DIV_MARGIN  = 6;    // contenido -> línea divisoria
    private static final int PREVIEW_GAP = 8;    // línea divisoria -> techo del preview
    // Proporción de ANCHO respecto al alto disponible — la misma que ya tenía el preview fijo
    // de esta pantalla (70/50) antes de este cambio.
    private static final float PREVIEW_W_RATIO = 1.4f;
    private static final int PREVIEW_W_MIN = 70, PREVIEW_W_MAX = 100;
    // El TAMAÑO del jugador (el parámetro de zoom, no el recuadro) casi no depende del alto
    // disponible a propósito: la primera versión lo derivaba del mismo boxH que el recuadro, y
    // eso castigaba justo a la raza con MÁS contenido que editar (Arcosiano, 6 filas -> el
    // recuadro más apretado de las 4 combinaciones) con el jugador más pequeño de todas — al
    // revés de lo deseable. El suelo ya es generoso (bastante por encima del tamaño fijo que
    // tenía antes esta pantalla) y el rango hasta el techo es estrecho: crece un poco cuando
    // sobra mucho hueco (Human/Saiyan/Majin), pero nunca se desploma por tener menos hueco.
    private static final float PREVIEW_SIZE_RATIO = 0.64f;
    private static final int PREVIEW_SIZE_MIN = 42, PREVIEW_SIZE_MAX = 52;

    // ── Zoom con rueda sobre el preview + botón de reset (lupa) — mismo criterio que
    // HeadAppearanceScreen (ver su javadoc): el recuadro no cambia con el zoom, solo el
    // "size"/escala del jugador dentro (acercar recorta bordes por el scissor del propio
    // renderEntityInInventoryFollowsMouse, comportamiento esperado de un zoom con viewport fijo).
    private static final float ZOOM_MIN = 0.6f, ZOOM_MAX = 1.8f, ZOOM_STEP = 0.1f;
    private static final int ZOOMED_SIZE_MIN = 20, ZOOMED_SIZE_MAX = 110;
    // Celda de icons.png reservada para pintarse a mano, ver HeadAppearanceScreen.
    private static final int ICON_RESET_VIEW_U = 0, ICON_RESET_VIEW_V = 100;

    // ── Rejilla de tonos (Human/Saiyan/Majin) — anclada arriba, sin centrar: el hueco que deja
    // libre ahora lo aprovecha el preview (ver arriba), no un margen muerto. ──
    private static final int TONE_W = 28, TONE_H = 22, TONE_GAP = 10;
    private static final int TONE_LABEL_H = 14;
    private static final int DEFAULT_BTN_H = 14;

    // ── Botón de género con icono de pixel art (celdas 80,80 / 100,80 de icons.png, las del hub) ──
    private static final ResourceLocation ICONS_TEX =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/icons.png");
    private static final int ICONS_ATLAS = 256;
    private static final int GENDER_ICON = 20;
    private static final int ICON_GENDER_MALE_U = 80, ICON_GENDER_FEMALE_U = 100, ICON_GENDER_V = 80;
    private int genderIconX, genderIconY;

    private static final int COLOR_SWATCH = ZenkaiPalette.LABEL_ON_PANEL;

    /**
     * El primero es EXACTAMENTE el tono principal del arte humano (F9C9AE, la "ref" de las capas
     * human_player_layer_N): elegido, el cuerpo teñible se ve igual que con "Default" (2026-10-12).
     */
    private static final int[] SKIN_TONES = { 0xF9C9AE, 0xEAB58E, 0xD5A07A, 0xC68642, 0x8D5524, 0x5C3A21 };

    private final AppearanceScreen hub;
    private final CompoundTag statsSnapshot;
    private final CompoundTag visualSnapshot;

    private boolean goingToHub = false, goingNext = false;
    private int panelLeft, panelTop;
    private Race race = Race.HUMAN;

    private int skinColor = 0xFFD5A07A;
    private boolean customSkinColor = false;
    private boolean genderFemale = false;
    private int toneLabelY;
    /** Y absoluta (pantalla) donde termina el contenido real de la raza actual — la fija
     *  buildLayerRows()/buildSkinToneGrid(), la lee render() para el divisor y el preview. */
    private int contentBottom;
    /** Estado de vista puro (como scroll): NO se guarda en statsSnapshot/visualSnapshot ni se
     *  toca en removed() — nace en 1.0 porque esta pantalla siempre se crea de cero. */
    private float zoomMult = 1.0f;

    private final java.util.List<RaceLayerDiscovery.Layer> tintLayers = new java.util.ArrayList<>();
    private final java.util.Map<Integer, Integer> layerArgb = new java.util.HashMap<>();
    private int activeLayerIndex = -1;
    private int scroll = 0;

    private boolean skinPickerActive = false;
    @Nullable private ColorPickerWidget picker = null;

    /**
     * Skin predeterminada (Human/Saiyan con "Skin Predeterminada" en la pantalla de raza): se ve la
     * skin vanilla del jugador, sin cuerpo de raza, así que el tono de piel no se pinta en ningún
     * sitio. La rejilla de tonos se enseña oscurecida y bloqueada, con tooltip.
     */
    private boolean vanillaSkin = false;

    /**
     * Arcosiano: qué forma enseña la vista previa (petición del usuario, 2026-10-11 — el editor se
     * usa en la forma base y no había forma de ver cómo quedan los colores en la Definitiva). Las
     * cuatro formas comparten la misma numeración de capas, así que un color tiñe la misma parte
     * en todas; las filas que la forma elegida no usa se oscurecen (ver veiledRows). Estado de
     * vista puro, como zoomMult: no se guarda ni se sincroniza.
     */
    private static final ResourceLocation[] PREVIEW_FORMS = {
            com.hmc.zenkai.feature.forms.FormIds.BASE,
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "second_form"),
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "third_form"),
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "final_form")};
    private int previewForm = 0;
    @Nullable private TextOnlyButton previewFormBtn;
    /** Índices de capa que la forma de la vista previa NO pinta (sus filas se oscurecen). */
    private final java.util.Set<Integer> veiledLayers = new java.util.HashSet<>();
    /** Rectángulos (x1, y1, x2, y2) de los controles de tono bloqueados, para el velo de render(). */
    private final java.util.List<int[]> lockedRects = new java.util.ArrayList<>();

    public BodyColorsScreen(AppearanceScreen hub,
                             @Nullable CompoundTag statsSnapshot, @Nullable CompoundTag visualSnapshot) {
        super(Component.translatable("screen.zenkai.appearance.title"));
        this.hub            = hub;
        this.statsSnapshot  = statsSnapshot;
        this.visualSnapshot = visualSnapshot;
    }

    @Override
    protected void init() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        this.clearWidgets();
        this.panelLeft = (this.width  - BG_W) / 2;
        this.panelTop  = (this.height - BG_H) / 2;

        var stats  = mc.player.getData(ZenkaiDataAttachments.PLAYER_STATS.get());
        var visual = mc.player.getData(ZenkaiDataAttachments.PLAYER_VISUAL.get());
        this.race        = stats.getRace();
        skinColor         = visual.getSkinColorRgb() | 0xFF000000;
        customSkinColor   = visual.isCustomSkinColor();
        // Partida que seguía en "Default" (botón retirado): es el primer tono, mismo aspecto.
        if (!customSkinColor && (stats.getRace() == Race.HUMAN || stats.getRace() == Race.SAIYAN)) {
            customSkinColor = true;
            skinColor = 0xFF000000 | SKIN_TONES[0];
        }
        genderFemale      = visual.getGender() == com.hmc.zenkai.feature.player.PlayerVisualAttachment.Gender.FEMALE;
        vanillaSkin       = !visual.shouldRenderRaceSkin();
        lockedRects.clear();

        if (isMultiTintRace(race)) buildLayerRows();
        else                       buildSkinToneGrid();

        int[] rect = previewRect();
        var resetViewBtn = new AtlasIconButton(rect[2] - 18, rect[1] + 2,
                ICON_RESET_VIEW_U, ICON_RESET_VIEW_V, () -> zoomMult = 1.0f);
        resetViewBtn.setTooltip(Tooltip.create(Component.translatable("screen.zenkai.appearance.reset_view")));
        addRenderableWidget(resetViewBtn);

        previewFormBtn = null;
        if (race == Race.ARCOSIAN) {
            int w = IN_X2 - IN_X1 - 2 * BTN_W - 8;
            previewFormBtn = new TextOnlyButton(
                    panelLeft + (BG_W - w) / 2, panelTop + BTN_BAR_Y, w, 20,
                    previewFormLabel(), TEX_BTN, null, this::cyclePreviewForm);
            previewFormBtn.setTooltip(Tooltip.create(Component.translatable("screen.zenkai.body.preview_form.tooltip")));
            addRenderableWidget(previewFormBtn);
        }
        refreshVeiledLayers();

        addRenderableWidget(new TextOnlyButton(
                panelLeft + IN_X1, panelTop + BTN_BAR_Y, BTN_W, 20,
                Component.translatable("screen.zenkai.back"),
                TEX_BTN, null,
                () -> {
                    // Back vuelve a la ÚLTIMA pestaña de la cabeza, no al hub: el flujo es hub → cabeza (pestañas) → cuerpo → estilo.
                    goingToHub = true;
                    mc.setScreen(new HeadAppearanceScreen(hub, statsSnapshot, visualSnapshot, true));
                }));

        addRenderableWidget(new TextOnlyButton(
                panelLeft + IN_X2 - BTN_W, panelTop + BTN_BAR_Y, BTN_W, 20,
                Component.translatable("screen.zenkai.next"),
                TEX_BTN, null,
                this::goToStyle));
    }

    /** Nombres CORTOS propios (no los form.zenkai.*): "Ver: Forma Final" con el prefijo no cabe bien en el
     *  hueco entre Back y Next, y el botón aún añade "[ ]" al pasar el ratón. */
    private static final String[] PREVIEW_FORM_KEYS = {"first", "second", "third", "final"};

    private Component previewFormLabel() {
        Component name = Component.translatable("screen.zenkai.body.preview_form." + PREVIEW_FORM_KEYS[previewForm]);
        return Component.translatable("screen.zenkai.body.preview_form", name);
    }

    private void cyclePreviewForm() {
        previewForm = (previewForm + 1) % PREVIEW_FORMS.length;
        if (previewFormBtn != null) previewFormBtn.setMessage(previewFormLabel());
        refreshVeiledLayers();
    }

    /** Capas que el cuerpo de la forma de la vista previa no tiene (o tiene vacías). */
    private void refreshVeiledLayers() {
        veiledLayers.clear();
        if (race != Race.ARCOSIAN || previewForm == 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        ItemStack body;
        com.hmc.zenkai.feature.forms.FormSkinResolver.setPreviewForm(PREVIEW_FORMS[previewForm]);
        try {
            body = com.hmc.zenkai.feature.forms.FormSkinResolver.resolveBodyOverride(mc.player, EquipmentSlot.CHEST);
        } finally {
            com.hmc.zenkai.feature.forms.FormSkinResolver.setPreviewForm(null);
        }
        if (!(body.getItem() instanceof GeoLayerArmorItem gi)) return;
        java.util.Set<Integer> present = new java.util.HashSet<>();
        for (RaceLayerDiscovery.Layer L : RaceLayerDiscovery.layersFor(gi)) present.add(L.index());
        for (RaceLayerDiscovery.Layer L : tintLayers) {
            if (!present.contains(L.index())) veiledLayers.add(L.index());
        }
    }

    private int rowsTop()       { return panelTop + ROWS_TOP; }
    private int listLeft()      { return panelLeft + IN_X1 + PAD; }
    private int trackRight()    { return panelLeft + IN_X2 - PAD; }
    private int scrollbarX()    { return trackRight() - SCROLLBAR_W; }
    private int listRight()     { return scrollbarX() - SCROLLBAR_GAP; }

    /** Recuadro (x1,y1,x2,y2) del preview — el mismo que dibuja render(), extraído para que
     *  init() (botón de reset) y mouseScrolled() (hover) no dupliquen la fórmula. Independiente
     *  de zoomMult: el recuadro no cambia con el zoom, solo el "size" del jugador dentro. Solo
     *  válido tras buildLayerRows()/buildSkinToneGrid(), que fijan contentBottom. */
    private int[] previewRect() {
        int divY      = contentBottom + DIV_MARGIN;
        int previewY1 = divY + PREVIEW_GAP;
        int previewY2 = panelTop + IN_Y2 - 4;
        int boxH      = Math.max(1, previewY2 - previewY1);
        int previewW  = Mth.clamp(Math.round(boxH * PREVIEW_W_RATIO), PREVIEW_W_MIN, PREVIEW_W_MAX);
        int cx        = panelLeft + BG_W / 2;
        return new int[] { cx - previewW / 2, previewY1, cx + previewW / 2, previewY2 };
    }

    private boolean isTintRace(Race r) {
        return r == Race.HUMAN || r == Race.SAIYAN || r == Race.MAJIN;
    }

    private boolean isMultiTintRace(Race r) {
        return r == Race.NAMEKIAN || r == Race.ARCOSIAN;
    }

    // ── Namek/Arcosian: una fila por capa, ancho completo, con scrollbar de seguridad ──

    private int visibleRows() { return Math.max(1, (ROWS_BOTTOM_MAX - ROWS_TOP) / ROW_H); }
    private int maxScroll()   { return Math.max(0, tintLayers.size() - visibleRows()); }
    private int rowTop(int i) { return rowsTop() + (i - scroll) * ROW_H; }
    private boolean onScreen(int i) {
        int rel = i - scroll;
        return rel >= 0 && rel < visibleRows();
    }

    /**
     * Razas multicolor: una fila de color por CAPA descubierta (genérico, data-driven).
     * index 0 = piel (escribe skinColorRgb); index >= 1 = capa (escribe layerColors[index]).
     * layerArgb/tintLayers guardan TODAS las capas (incluidas las que el scroll deja fuera de
     * pantalla ahora mismo) — solo los WIDGETS de fila se limitan a las visibles, así que
     * applyPreview() nunca pierde el color de una capa por estar scrolleada fuera de vista.
     */
    private void buildLayerRows() {
        customSkinColor = true; // multicolor siempre coloreable
        tintLayers.clear();
        layerArgb.clear();

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        var visual = mc.player.getData(ZenkaiDataAttachments.PLAYER_VISUAL.get());

        ItemStack body = RaceSkinSlots.getVirtualRaceArmor(mc.player, EquipmentSlot.CHEST);
        if (!(body.getItem() instanceof GeoLayerArmorItem gi)) return;

        for (RaceLayerDiscovery.Layer L : RaceLayerDiscovery.layersFor(gi)) {
            int idx = L.index();
            int cur = (idx == 0)
                    ? visual.getSkinColorRgb()
                    : (visual.hasLayerColor(idx) ? visual.getLayerColorRgb(idx) : L.defaultRgb());
            layerArgb.put(idx, 0xFF000000 | (cur & 0xFFFFFF));
            tintLayers.add(L);
        }

        scroll = Mth.clamp(scroll, 0, maxScroll());

        for (int i = 0; i < tintLayers.size(); i++) {
            if (!onScreen(i)) continue;
            buildLayerRowWidgets(tintLayers.get(i), rowTop(i));
        }

        // Cuánto de la zona de filas ocupa contenido de verdad: si hay menos capas de las que
        // caben (Namekiano, 3 < 6 de Arcosiano), el divisor sube y el preview se queda con el
        // resto. Si hay tantas o más que visibleRows() (scrolleando), se queda en el máximo —
        // mismo punto de siempre, el preview no "respira" con el scroll.
        int usedRows = Math.min(tintLayers.size(), visibleRows());
        contentBottom = rowsTop() + usedRows * ROW_H;
    }

    private void buildLayerRowWidgets(RaceLayerDiscovery.Layer L, int y) {
        int[] presets = L.presets();
        int gap = 4;
        int totalW = presets.length * (PRESET_BOX_W + gap) + COLOR_BOX_W;
        int x = listRight() - totalW;
        int presetY = y + (ROW_H - PRESET_BOX_H) / 2;

        for (int c : presets) {
            final int col = c;
            addRenderableWidget(new ColorBoxButton(x, presetY, PRESET_BOX_W, PRESET_BOX_H,
                    () -> col,
                    () -> (layerCurrent(L) & 0xFFFFFF) == col,
                    () -> { layerArgb.put(L.index(), 0xFF000000 | col); closePicker(); applyPreview(); }));
            x += PRESET_BOX_W + gap;
        }

        int boxY = y + (ROW_H - COLOR_BOX_H) / 2;
        var custom = new ColorBoxButton(x, boxY, COLOR_BOX_W, COLOR_BOX_H,
                () -> layerCurrent(L) & 0xFFFFFF,
                () -> activeLayerIndex == L.index(),
                () -> toggleLayerPicker(L));
        custom.setTooltip(Tooltip.create(Component.translatable("screen.zenkai.body.custom_color")));
        addRenderableWidget(custom);
    }

    private int layerCurrent(RaceLayerDiscovery.Layer L) {
        return layerArgb.getOrDefault(L.index(), 0xFF000000 | L.defaultRgb());
    }

    private void toggleLayerPicker(RaceLayerDiscovery.Layer L) {
        if (activeLayerIndex == L.index() && picker != null) { closePicker(); return; }
        openLayerPicker(L);
    }

    private void openLayerPicker(RaceLayerDiscovery.Layer L) {
        closePicker();
        activeLayerIndex = L.index();
        int pickerX = panelLeft + BG_W + 8;
        if (pickerX + ColorPickerWidget.TOTAL_W > this.width - 4)
            pickerX = panelLeft - ColorPickerWidget.TOTAL_W - 8;
        picker = new ColorPickerWidget(pickerX, panelTop + IN_Y1, layerCurrent(L), L.labelComponent().getString(), argb -> {
            layerArgb.put(L.index(), 0xFF000000 | (argb & 0xFFFFFF));
            applyPreview();
        });
        addRenderableWidget(picker);
    }

    private void renderLayerRows(GuiGraphics g) {
        if (tintLayers.isEmpty()) return;

        g.enableScissor(panelLeft, rowsTop(), panelLeft + BG_W, rowsTop() + visibleRows() * ROW_H);
        for (int i = 0; i < tintLayers.size(); i++) {
            if (!onScreen(i)) continue;
            RaceLayerDiscovery.Layer L = tintLayers.get(i);
            int y = rowTop(i);
            PanelText.onPanel(g, this.font, L.labelComponent(), listLeft(), y + (ROW_H - 8) / 2, COLOR_SWATCH);
        }
        g.disableScissor();
        drawScrollbar(g);
    }

    private void drawScrollbar(GuiGraphics g) {
        int max = maxScroll();
        if (max <= 0) return;

        int x = scrollbarX();
        int trackTop = rowsTop(), trackH = visibleRows() * ROW_H;
        g.fill(x, trackTop, x + SCROLLBAR_W, trackTop + trackH, ZenkaiPalette.BAR_BG);

        int count = tintLayers.size();
        int thumbH = Math.max(12, trackH * visibleRows() / count);
        int thumbY = trackTop + (trackH - thumbH) * scroll / max;
        g.fill(x, thumbY, x + SCROLLBAR_W, thumbY + thumbH, ZenkaiPalette.VALUE_ON_PANEL);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double dx, double dy) {
        int[] rect = previewRect();
        if (mouseX >= rect[0] && mouseX < rect[2] && mouseY >= rect[1] && mouseY < rect[3]) {
            zoomMult = Mth.clamp(zoomMult + (dy > 0 ? ZOOM_STEP : -ZOOM_STEP), ZOOM_MIN, ZOOM_MAX);
            return true;
        }
        if (maxScroll() > 0) {
            // rebuildWidgets() -> init() hace clearWidgets(), que se llevaría el
            // ColorPickerWidget por delante sin avisar (el campo picker quedaría apuntando a
            // un widget ya no registrado) — cerrarlo primero, mismo cuidado que
            // AppearanceScreen.switchMode() ya tenía para el mismo problema.
            closePicker();
            scroll = Mth.clamp(scroll - (int) Math.signum(dy), 0, maxScroll());
            this.rebuildWidgets();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, dx, dy);
    }

    // ── Human/Saiyan/Majin: rejilla de tonos, centrada en el área de filas ──

    private void buildSkinToneGrid() {
        int[] presets = SKIN_TONES;
        int perRow = 4;
        int total  = presets.length + 1;
        int rows   = (total + perRow - 1) / perRow;

        // Anclado arriba, sin centrar: el hueco libre que esto deje lo aprovecha el preview
        // dinámico (ver contentBottom más abajo), no un margen muerto encima y debajo.
        toneLabelY = rowsTop();
        int gridTop = toneLabelY + TONE_LABEL_H;
        int cx = panelLeft + BG_W / 2;

        for (int i = 0; i < total; i++) {
            int row = i / perRow, col = i % perRow;
            int countInRow = Math.min(perRow, total - row * perRow);
            int rowW   = countInRow * TONE_W + (countInRow - 1) * TONE_GAP;
            int startX = cx - rowW / 2;
            int x = startX + col * (TONE_W + TONE_GAP);
            int y = gridTop + row * (TONE_H + TONE_GAP);

            if (i == presets.length) {
                var custom = new ColorBoxButton(x, y, TONE_W, TONE_H,
                        () -> skinColor & 0xFFFFFF,
                        () -> skinPickerActive,
                        this::toggleSkinPicker);
                custom.setTooltip(Tooltip.create(Component.translatable("screen.zenkai.body.custom_color")));
                lockForVanillaSkin(custom);
                addRenderableWidget(custom);
            } else {
                final int c = presets[i];
                var swatch = new ColorBoxButton(x, y, TONE_W, TONE_H,
                        () -> c,
                        () -> customSkinColor && (skinColor & 0xFFFFFF) == c,
                        () -> { customSkinColor = true; skinColor = 0xFF000000 | c; closePicker(); applyPreview(); });
                lockForVanillaSkin(swatch);
                addRenderableWidget(swatch);
            }
        }

        // Sin botón "Default" (2026-10-12): el primer tono (F9C9AE) ya es idéntico al cuerpo por
        // defecto gracias a las capas de piel (human_player_layer_N, LayerTone), así que el botón
        // sobraba y además el primer tono antes NO coincidía con él (bug).
        int gridBottom = gridTop + rows * (TONE_H + TONE_GAP) - TONE_GAP;
        contentBottom = gridBottom + 2;

        // Género: mismo ajuste que la fila "Gender" del hub, aquí para que el flujo lineal
        // cabeza → cuerpo → estilo lo incluya sin volver al hub. Solo razas de tinte simple
        // (las que tienen modelo masculino/femenino); esta rejilla ya es solo de ellas.
        int genderY = contentBottom + 6;
        Component genderName = Component.translatable(genderFemale
                ? "screen.zenkai.body.gender.female" : "screen.zenkai.body.gender.male");
        // Icono (celda 20x20 de icons.png, las mismas que la fila del hub) + botón a su derecha.
        // El botón mide lo que su texto (el texto va centrado en el botón): así queda pegado al
        // icono y el conjunto se centra de verdad, sea cual sea el idioma o el género.
        Component genderLabel = Component.translatable("screen.zenkai.body.gender", genderName);
        int genderBtnW = Minecraft.getInstance().font.width(genderLabel) + 8;
        genderIconX = cx - (GENDER_ICON + 4 + genderBtnW) / 2;
        genderIconY = genderY;
        addRenderableWidget(new TextOnlyButton(genderIconX + GENDER_ICON + 4, genderY + (GENDER_ICON - DEFAULT_BTN_H) / 2,
                genderBtnW, DEFAULT_BTN_H,
                genderLabel,
                () -> { genderFemale = !genderFemale; closePicker(); applyPreview(); rebuildWidgets(); })
                .onPanel());
        contentBottom = genderY + GENDER_ICON;
    }

    /**
     * Con la skin predeterminada desactiva el control (active=false: no responde al clic, pero
     * AbstractWidget sigue calculando el hover y enseñando el tooltip con el motivo) y anota su
     * recuadro para el velo oscuro de render().
     */
    private void lockForVanillaSkin(net.minecraft.client.gui.components.AbstractWidget w) {
        if (!vanillaSkin) return;
        w.active = false;
        w.setTooltip(Tooltip.create(Component.translatable("screen.zenkai.body.locked_vanilla_skin")));
        lockedRects.add(new int[]{w.getX() - 1, w.getY() - 1, w.getX() + w.getWidth() + 1, w.getY() + w.getHeight() + 1});
    }

    private void renderSkinToneLabel(GuiGraphics g) {
        int cx = panelLeft + BG_W / 2;
        drawCenteredNoShadow(g, Component.literal("Skin Color"),
                cx, toneLabelY, COLOR_SWATCH);
    }

    // ── Color picker de piel (razas de tinte simple) ────────────────────────

    private void toggleSkinPicker() {
        if (skinPickerActive && picker != null) { closePicker(); return; }
        openSkinPicker();
    }

    private void openSkinPicker() {
        closePicker();
        skinPickerActive = true;
        customSkinColor = true;
        int pickerX = panelLeft + BG_W + 8;
        if (pickerX + ColorPickerWidget.TOTAL_W > this.width - 4)
            pickerX = panelLeft - ColorPickerWidget.TOTAL_W - 8;
        picker = new ColorPickerWidget(pickerX, panelTop + IN_Y1, skinColor, "Skin Color", argb -> {
            skinColor = argb; customSkinColor = true; applyPreview();
        });
        addRenderableWidget(picker);
    }

    private void closePicker() {
        if (picker != null) { removeWidget(picker); picker = null; }
        skinPickerActive = false;
        activeLayerIndex = -1;
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

        if (isMultiTintRace(race)) renderLayerRows(g);
        else                       renderSkinToneLabel(g);

        // Divisor y preview DINÁMICOS: contentBottom es dónde termina el contenido real de
        // ESTA raza (ver buildLayerRows/buildSkinToneGrid) — cuanto menos contenido, más
        // hueco le queda al preview, en vez de un tamaño fijo pensado para el peor caso.
        int divY = contentBottom + DIV_MARGIN;
        g.fill(pl + IN_X1 + PAD, divY, pl + IN_X2 - PAD, divY + 1, ZenkaiPalette.SEPARATOR);

        int[] rect = previewRect();
        int boxH = Math.max(1, rect[3] - rect[1]);
        int previewSize = Mth.clamp(Math.round(boxH * PREVIEW_SIZE_RATIO), PREVIEW_SIZE_MIN, PREVIEW_SIZE_MAX);
        int zoomedSize   = Mth.clamp(Math.round(previewSize * zoomMult), ZOOMED_SIZE_MIN, ZOOMED_SIZE_MAX);

        // La forma elegida en el botón de vista previa solo vale para ESTE render (try/finally).
        if (race == Race.ARCOSIAN) com.hmc.zenkai.feature.forms.FormSkinResolver.setPreviewForm(PREVIEW_FORMS[previewForm]);
        try {
            InventoryScreen.renderEntityInInventoryFollowsMouse(
                    g, rect[0], rect[1], rect[2], rect[3],
                    zoomedSize, 0.0625f, (float) mouseX, (float) mouseY, mc.player);
        } finally {
            com.hmc.zenkai.feature.forms.FormSkinResolver.setPreviewForm(null);
        }

        super.render(g, mouseX, mouseY, partialTick);
        // Velo oscuro sobre los controles bloqueados (después de los widgets: las casillas de color
        // se pintan opacas, así que un velo por debajo no se vería).
        for (int[] r : lockedRects) g.fill(r[0], r[1], r[2], r[3], 0x88000000);
        // Filas de capas que la forma de la vista previa no usa: mismo velo, más suave (se pueden
        // seguir editando; afectan a las otras formas).
        for (int i = 0; i < tintLayers.size(); i++) {
            if (!onScreen(i) || !veiledLayers.contains(tintLayers.get(i).index())) continue;
            int y = rowTop(i);
            g.fill(listLeft() - 2, y + 1, listRight() + 2, y + ROW_H - 1, 0x66000000);
        }

        if (!isMultiTintRace(race)) {
            g.blit(ICONS_TEX, genderIconX, genderIconY,
                    genderFemale ? ICON_GENDER_FEMALE_U : ICON_GENDER_MALE_U, ICON_GENDER_V,
                    GENDER_ICON, GENDER_ICON, ICONS_ATLAS, ICONS_ATLAS);
        }
    }

    private void drawCenteredNoShadow(GuiGraphics g, Component text, int cx, int y, int color) {
        PanelText.centeredOnPanel(g, Minecraft.getInstance().font, text, cx, y, color);
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

    private void applyPreview() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        var visual = mc.player.getData(ZenkaiDataAttachments.PLAYER_VISUAL.get());
        visual.setSkinColorRgb(skinColor & 0xFFFFFF);
        visual.setCustomSkinColor(customSkinColor);
        if (isTintRace(race)) {
            visual.setGender(genderFemale ? com.hmc.zenkai.feature.player.PlayerVisualAttachment.Gender.FEMALE
                    : com.hmc.zenkai.feature.player.PlayerVisualAttachment.Gender.MALE);
        }

        // Razas multicolor: capa 0 -> piel (skinColorRgb, sobrescribe lo de arriba); capa >=1
        // -> layerColors[index]. Corre SIEMPRE que la raza sea multicolor, sin importar qué
        // fila se tocó — layerArgb tiene TODAS las capas (ver buildLayerRows), no solo la
        // visible, así que scrollear no pierde nada.
        if (isMultiTintRace(race)) {
            for (var e : layerArgb.entrySet()) {
                int idx = e.getKey(), rgb = e.getValue() & 0xFFFFFF;
                if (idx == 0) visual.setSkinColorRgb(rgb);
                else          visual.setLayerColorRgb(idx, rgb);
            }
        }
    }

    private void goToStyle() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        goingNext = true;
        mc.setScreen(new StyleSelectionScreen(hub, statsSnapshot, visualSnapshot));
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
