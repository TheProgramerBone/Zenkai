package com.hmc.zenkai.client.gui.screens;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.CustomizationAssets;
import com.hmc.zenkai.client.gui.ScreenTitle;
import com.hmc.zenkai.client.gui.buttons.ArrowIconButton;
import com.hmc.zenkai.client.gui.buttons.AtlasIconButton;
import com.hmc.zenkai.client.gui.buttons.TextOnlyButton;
import com.hmc.zenkai.client.gui.widgets.ColorBoxButton;
import com.hmc.zenkai.client.gui.widgets.ColorPickerWidget;
import com.hmc.zenkai.feature.Race;
import com.hmc.zenkai.registry.ZenkaiDataAttachments;
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
 * Sección "Head" del hub de apariencia ({@link AppearanceScreen}): ojos (estilo+color), pelo
 * (estilo+color, solo Human/Saiyan), boca, nariz. Contenido idéntico al que tenía el antiguo
 * modo HEAD de AppearanceScreen — el preview 3D se CENTRA bajo el divisor en vez de quedar
 * pegado a la columna izquierda (ya no comparte fila con nada, así que puede ocupar el centro).
 *
 * DIVISOR Y PREVIEW DINÁMICOS (mismo criterio que BodyColorsScreen, ver su javadoc): solo
 * Human/Saiyan tienen 4 campos (con Hair); el resto tiene 3 (Namek/Arcosian/Majin no tienen
 * pelo editable) — el divisor se dibuja justo debajo del último campo REAL, no de un hueco fijo
 * pensado para el caso de 4, así que esas razas dejan más aire (y más preview) que antes. El
 * ANCHO del preview escala con ese aire disponible; el TAMAÑO (el zoom del jugador, no el
 * recuadro) tiene un suelo alto y un rango estrecho — el suelo es el valor fijo que ya tenía
 * esta pantalla, así que el caso de 4 campos nunca queda más pequeño que antes.
 *
 * SINCRONIZACIÓN: se crea SIEMPRE de cero desde el hub (nunca se cachea/reutiliza) — no guarda
 * nada que no esté ya reflejado en PlayerVisualAttachment vía su propia applyPreview() (que
 * aquí solo escribe ojos/pelo/boca/nariz, NO piel/capas/género — de eso se encargan
 * BodyColorsScreen y el hub respectivamente). Un ÚNICO botón "Back" (el de la barra inferior,
 * sin fila "‹ Back" propia arriba — dos "Back" con comportamiento distinto en la misma pantalla
 * era justo la confusión que motivó separar esto en 3 Screen, ver AppearanceScreen) vuelve al
 * MISMO hub que creó esta pantalla (mc.setScreen(hub), nunca uno nuevo) marcando
 * goingToHub=true antes — removed() solo revierte si NINGUNO de los flags de salida
 * intencional se marcó, mismo contrato que ya usan RaceSelectionScreen/StyleSelectionScreen.
 */
public class HeadAppearanceScreen extends Screen {

    private static final ResourceLocation BG =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/common_screen.png");

    private static final ResourceLocation TEX_BTN =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/btn_wide.png");

    private static final int BG_W = 256;
    private static final int BG_H = 256;
    private static final int IN_X1 = 10;
    private static final int IN_Y1 = 10;
    private static final int IN_X2 = 245;
    private static final int IN_Y2 = 240;
    private static final int BTN_BAR_Y = 260;
    private static final int BTN_W     = 60;

    private static final int CONTENT_Y0 = IN_Y1 + 6;
    private static final int PAD          = 8;
    private static final int ARROW_W      = 12;
    private static final int TITLE_H      = 11;
    private static final int BLOCK_H      = 27;
    private static final int COLOR_BOX_W  = 12;
    private static final int COLOR_BOX_H  = 10;

    private static final int ARROW_LX            = IN_X1 + PAD;
    private static final int ARROW_RX_NO_COLOR   = IN_X2 - PAD - ARROW_W;
    private static final int ARROW_RX_WITH_COLOR = IN_X2 - PAD - ARROW_W - COLOR_BOX_W - 4;

    // ── Divisor y preview: ver javadoc de la clase. Suelo = los valores fijos que tenía esta
    // pantalla antes de este cambio (caso de 4 campos, Human/Saiyan); techo = un poco más,
    // para el caso de 3 campos sin quedar desproporcionado. ──
    private static final int DIV_MARGIN  = 6;
    private static final int PREVIEW_GAP = 8;
    private static final float PREVIEW_W_RATIO    = 0.784f;  // 80/102, el ratio de antes
    private static final float PREVIEW_SIZE_RATIO = 0.441f;  // 45/102, el ratio de antes
    private static final int PREVIEW_W_MIN = 80, PREVIEW_W_MAX = 95;
    private static final int PREVIEW_SIZE_MIN = 45, PREVIEW_SIZE_MAX = 52;

    // ── Zoom con rueda sobre el preview + botón de reset (lupa) — ver javadoc de la clase.
    // El recuadro (x1,y1,x2,y2) NO cambia con el zoom, solo el "size"/escala del jugador
    // dentro de él: renderEntityInInventoryFollowsMouse hace scissor al recuadro, así que
    // acercar recorta los bordes (comportamiento esperado de un zoom sobre un viewport fijo).
    private static final float ZOOM_MIN = 0.6f, ZOOM_MAX = 1.8f, ZOOM_STEP = 0.1f;
    private static final int ZOOMED_SIZE_MIN = 20, ZOOMED_SIZE_MAX = 110;
    // Celda de icons.png completamente libre (fila v=100), reservada para pintarse a mano —
    // mismo trato que ICON_GENDER_MALE_U/V/ICON_GENDER_FEMALE_U/V en AppearanceScreen: un
    // glifo compuesto (lupa = aro + mango) no se generó por script, ver gen_appearance_icons.py.
    private static final int ICON_RESET_VIEW_U = 0, ICON_RESET_VIEW_V = 100;

    private static final int COLOR_TITLE = ZenkaiPalette.LABEL_ON_PANEL;
    private static final int COLOR_VALUE = ZenkaiPalette.VALUE_ON_PANEL;

    private enum ColorChannel { EYE, HAIR }

    private final AppearanceScreen hub;
    private final CompoundTag statsSnapshot;
    private final CompoundTag visualSnapshot;

    private boolean goingToHub = false, goingNext = false;
    private int panelLeft, panelTop;
    private Race race = Race.HUMAN;

    private int eyeIndex, hairIndex, mouthIndex, noseIndex;
    private int eyeColor = 0xFF2E86C1, hairColor = 0xFF1A1A1A;
    /** Estado de vista puro (como el "scroll" de BodyColorsScreen): NO se guarda en
     *  statsSnapshot/visualSnapshot ni se toca en removed() — nace en 1.0 porque esta pantalla
     *  siempre se crea de cero. */
    private float zoomMult = 1.0f;

    @Nullable private ColorChannel      activeChannel = null;
    @Nullable private ColorPickerWidget picker        = null;

    public HeadAppearanceScreen(AppearanceScreen hub,
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
        this.race  = stats.getRace();
        eyeColor   = visual.getEyeColorRgb()  | 0xFF000000;
        hairColor  = visual.getHairColorRgb() | 0xFF000000;
        eyeIndex   = visual.getEyeIndex();
        hairIndex  = visual.getHairIndex();
        mouthIndex = visual.getMouthIndex();
        noseIndex  = visual.getNoseIndex();

        CustomizationAssets.reload();

        buildHeadFields(panelLeft);

        int[] rect = previewRect();
        var resetViewBtn = new AtlasIconButton(rect[2] - 18, rect[1] + 2,
                ICON_RESET_VIEW_U, ICON_RESET_VIEW_V, () -> zoomMult = 1.0f);
        resetViewBtn.setTooltip(Tooltip.create(Component.translatable("screen.zenkai.appearance.reset_view")));
        addRenderableWidget(resetViewBtn);

        addRenderableWidget(new TextOnlyButton(
                panelLeft + IN_X1, panelTop + BTN_BAR_Y, BTN_W, 20,
                Component.translatable("screen.zenkai.back"),
                TEX_BTN, null,
                () -> { goingToHub = true; mc.setScreen(hub); }));

        addRenderableWidget(new TextOnlyButton(
                panelLeft + IN_X2 - BTN_W, panelTop + BTN_BAR_Y, BTN_W, 20,
                Component.translatable("screen.zenkai.next"),
                TEX_BTN, null,
                this::goToStyle));
    }

    private int contentTop() { return panelTop + CONTENT_Y0; }
    private int fieldsTop()  { return contentTop(); }

    /** Human/Saiyan tienen el campo Hair de más — el resto se queda en 3. */
    private int fieldCount() { return (race == Race.HUMAN || race == Race.SAIYAN) ? 4 : 3; }

    private int contentBottom() { return fieldsTop() + fieldCount() * BLOCK_H; }
    private int divY()          { return contentBottom() + DIV_MARGIN; }
    private int bottomZoneY()   { return divY() + PREVIEW_GAP; }

    /** Recuadro (x1,y1,x2,y2) del preview — el mismo que dibuja render(), extraído para que
     *  init() (botón de reset) y mouseScrolled() (hover) no dupliquen la fórmula. Independiente
     *  de zoomMult: el recuadro no cambia con el zoom, solo el "size" del jugador dentro. */
    private int[] previewRect() {
        int boxH     = Math.max(1, (panelTop + IN_Y2 - 4) - bottomZoneY());
        int previewW = Mth.clamp(Math.round(boxH * PREVIEW_W_RATIO), PREVIEW_W_MIN, PREVIEW_W_MAX);
        int previewCX = panelLeft + BG_W / 2;
        return new int[] { previewCX - previewW / 2, bottomZoneY(), previewCX + previewW / 2, panelTop + IN_Y2 - 4 };
    }

    private void buildHeadFields(int pl) {
        int blockTop = fieldsTop();

        blockTop = addField(pl, blockTop, ColorChannel.EYE,
                () -> eyeIndex = (eyeIndex - 1 + CustomizationAssets.eyesCount()) % CustomizationAssets.eyesCount(),
                () -> eyeIndex = (eyeIndex + 1) % CustomizationAssets.eyesCount());

        if (race == Race.HUMAN || race == Race.SAIYAN) {
            blockTop = addField(pl, blockTop, ColorChannel.HAIR,
                    () -> hairIndex = (hairIndex - 1 + CustomizationAssets.hairCount()) % CustomizationAssets.hairCount(),
                    () -> hairIndex = (hairIndex + 1) % CustomizationAssets.hairCount());
        }

        blockTop = addField(pl, blockTop, null,
                () -> mouthIndex = (mouthIndex - 1 + CustomizationAssets.mouthCount()) % CustomizationAssets.mouthCount(),
                () -> mouthIndex = (mouthIndex + 1) % CustomizationAssets.mouthCount());

        addField(pl, blockTop, null,
                () -> noseIndex = (noseIndex - 1 + CustomizationAssets.noseCount()) % CustomizationAssets.noseCount(),
                () -> noseIndex = (noseIndex + 1) % CustomizationAssets.noseCount());
    }

    private int addField(int pl, int blockTop, @Nullable ColorChannel channel,
                         Runnable onLeft, Runnable onRight) {
        int arrowY = blockTop + TITLE_H - 1;
        addRenderableWidget(new ArrowIconButton(
                pl + ARROW_LX, arrowY,
                ArrowIconButton.Dir.LEFT, () -> { onLeft.run(); applyPreview(); }));

        int rightX = pl + (channel != null ? ARROW_RX_WITH_COLOR : ARROW_RX_NO_COLOR);
        addRenderableWidget(new ArrowIconButton(
                rightX, arrowY,
                ArrowIconButton.Dir.RIGHT, () -> { onRight.run(); applyPreview(); }));

        if (channel != null) {
            final ColorChannel ch = channel;
            addRenderableWidget(new ColorBoxButton(
                    pl + IN_X2 - PAD - COLOR_BOX_W, arrowY,
                    COLOR_BOX_W, COLOR_BOX_H,
                    () -> colorForChannel(ch) & 0xFFFFFF,
                    () -> activeChannel == ch,
                    () -> togglePicker(ch)));
        }
        return blockTop + BLOCK_H;
    }

    // ── Color pickers (ojos/pelo) ───────────────────────────────────────────

    private void togglePicker(ColorChannel ch) {
        if (activeChannel == ch && picker != null) { closePicker(); return; }
        openPicker(ch);
    }

    private void openPicker(ColorChannel channel) {
        closePicker();
        activeChannel = channel;
        String label = channel == ColorChannel.EYE ? "Eye Color" : "Hair Color";
        int pickerX = panelLeft + BG_W + 8;
        if (pickerX + ColorPickerWidget.TOTAL_W > this.width - 4)
            pickerX = panelLeft - ColorPickerWidget.TOTAL_W - 8;
        picker = new ColorPickerWidget(pickerX, panelTop + IN_Y1, colorForChannel(channel), label, argb -> {
            if (channel == ColorChannel.EYE) eyeColor = argb; else hairColor = argb;
            applyPreview();
        });
        addRenderableWidget(picker);
    }

    private void closePicker() {
        if (picker != null) { removeWidget(picker); picker = null; }
        activeChannel = null;
    }

    private int colorForChannel(ColorChannel ch) {
        return ch == ColorChannel.EYE ? eyeColor : hairColor;
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

        int blockTop = fieldsTop();
        blockTop = renderField(g, mc, pl, blockTop, "Eyes", CustomizationAssets.eyeLabel(eyeIndex));
        if (race == Race.HUMAN || race == Race.SAIYAN)
            blockTop = renderField(g, mc, pl, blockTop, "Hair", CustomizationAssets.hairLabel(hairIndex));
        blockTop = renderField(g, mc, pl, blockTop, "Mouth", CustomizationAssets.mouthLabel(mouthIndex));
        renderField(g, mc, pl, blockTop, "Nose",  CustomizationAssets.noseLabel(noseIndex));

        g.fill(pl + IN_X1 + PAD, divY(), pl + IN_X2 - PAD, divY() + 1, ZenkaiPalette.SEPARATOR);

        // Preview CENTRADO y DINÁMICO — ver javadoc de la clase: crece cuando la raza actual
        // tiene menos campos (3 en vez de 4), nunca se encoge por debajo del tamaño de antes.
        int boxH = Math.max(1, (pt + IN_Y2 - 4) - bottomZoneY());
        int previewSize = Mth.clamp(Math.round(boxH * PREVIEW_SIZE_RATIO), PREVIEW_SIZE_MIN, PREVIEW_SIZE_MAX);
        int zoomedSize   = Mth.clamp(Math.round(previewSize * zoomMult), ZOOMED_SIZE_MIN, ZOOMED_SIZE_MAX);

        int[] rect = previewRect();
        InventoryScreen.renderEntityInInventoryFollowsMouse(
                g, rect[0], rect[1], rect[2], rect[3],
                zoomedSize, 0.0625f, (float) mouseX, (float) mouseY, mc.player);

        super.render(g, mouseX, mouseY, partialTick);
    }

    private int renderField(GuiGraphics g, Minecraft mc, int pl, int blockTop, String label, String value) {
        int cx = pl + BG_W / 2;
        drawCenteredNoShadow(g, Component.literal(label), cx, blockTop, COLOR_TITLE);
        PanelText.centeredOnPanel(g, mc.font,
                Component.literal(value), cx, blockTop + TITLE_H, COLOR_VALUE);
        return blockTop + BLOCK_H;
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

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double dx, double dy) {
        int[] rect = previewRect();
        if (mouseX >= rect[0] && mouseX < rect[2] && mouseY >= rect[1] && mouseY < rect[3]) {
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
        visual.setHairColorRgb(hairColor & 0xFFFFFF);
        visual.setEyeIndex(eyeIndex);
        visual.setHairIndex(hairIndex);
        visual.setMouthIndex(mouthIndex);
        visual.setNoseIndex(noseIndex);
        visual.setHairStyleId(hairIndex == 0 ? "hair0" : "hair" + hairIndex);
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
