package com.hmc.zenkai.client.gui.screens;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.customization.CustomizationAssets;
import com.hmc.zenkai.client.gui.buttons.ArrowIconButton;
import com.hmc.zenkai.client.gui.buttons.TextOnlyButton;
import com.hmc.zenkai.client.gui.widgets.ColorBoxButton;
import com.hmc.zenkai.client.gui.widgets.ColorPickerWidget;
import com.hmc.zenkai.core.network.feature.Race;
import com.hmc.zenkai.core.network.feature.stats.DataAttachments;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AppearanceScreen extends Screen {

    private static final ResourceLocation BG =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/common_screen.png");

    private static final int BG_W = 256;
    private static final int BG_H = 256;
    private static final int IN_X1 = 10;
    private static final int IN_Y1 = 10;
    private static final int IN_X2 = 245;
    private static final int IN_Y2 = 240;
    private static final int IN_W  = IN_X2 - IN_X1;
    private static final int BTN_BAR_Y = 260;
    private static final int BTN_W     = 60;

    private static final int PAD          = 8;
    private static final int ARROW_W      = 12;
    private static final int TITLE_H      = 11; // separación título → fila de valor
    private static final int BLOCK_H      = 27; // alto total de un campo (título + valor)
    private static final int COLOR_BOX_W  = 20;
    private static final int COLOR_BOX_H  = 12;

    // Flechas — siempre al mismo X
    private static final int ARROW_LX            = IN_X1 + PAD;
    private static final int ARROW_RX_NO_COLOR   = IN_X2 - PAD - ARROW_W;
    private static final int ARROW_RX_WITH_COLOR = IN_X2 - PAD - ARROW_W - COLOR_BOX_W - 4;

    private static final int PREVIEW_W    = 80;
    private static final int PREVIEW_SIZE = 45;

    // ── Colores de texto ──────────────────────────────────────────────────────
    private static final int COLOR_TITLE  = 0x4A3726; // marrón oscuro → título de campo (Eyes, Hair, Mouth, Nose)
    private static final int COLOR_VALUE  = 0xFFFFFF; // blanco+sombra → valor seleccionado (None, hair_1, ...)
    private static final int COLOR_SWATCH = 0x8A6A1E; // bronce/dorado → etiqueta de la sección de piel

    // Presets de piel.
    //  · Razas de tinte (Human/Saiyan/Majin): son COLORES aplicados por multiplicado al item colorable.
    //  · Razas con textura (Namekian/Arcosian): el color es solo representativo del swatch; el índice = skinPreset
    //    y DEBE coincidir con el nº de texturas .presets(...) registradas en ModItems para esa raza.
    private static final int[] SKIN_TONES       = { 0xF5C7AC, 0xEAB58E, 0xD5A07A, 0xC68642, 0x8D5524, 0x5C3A21 };
    private static final int[] NAMEKIAN_PRESETS = { 0x4CAF50, 0x2E7D32, 0x66BB6A };
    private static final int[] ARCOSIAN_PRESETS = { 0xFFFFFF, 0xE1BEE7, 0xFFE0B2 };

    @Nullable private final RaceSelectionScreen raceScreen;
    private final CompoundTag statsSnapshot;
    private final CompoundTag visualSnapshot;

    private boolean confirmed = false, goingBack = false, goingNext = false;
    private int panelLeft, panelTop;
    private int divY, bottomZoneY, skinAreaCX;

    private int eyeIndex = 0, hairIndex = 0, mouthIndex = 0, noseIndex = 0;
    private int skinColor = 0xFFD5A07A, eyeColor = 0xFF2E86C1;
    private int hairColor = 0xFF1A1A1A, detailColor = 0xFF9B59B6;
    private boolean customSkinColor = false; // Human/Saiyan/Majin: false=natural, true=tinte custom/preset
    private int     skinPreset      = 0;     // Namekian/Arcosian: índice de textura preset

    private enum ColorChannel { SKIN, EYE, HAIR, DETAIL }
    @Nullable private ColorChannel    activeChannel = null;
    @Nullable private ColorPickerWidget picker      = null;
    private Race race = Race.HUMAN;

    public AppearanceScreen(@Nullable RaceSelectionScreen raceScreen,
                            @Nullable CompoundTag statsSnapshot,
                            @Nullable CompoundTag visualSnapshot) {
        super(Component.translatable("screen.zenkai.appearance.title"));
        this.raceScreen    = raceScreen;
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

        var stats  = mc.player.getData(DataAttachments.PLAYER_STATS.get());
        var visual = mc.player.getData(DataAttachments.PLAYER_VISUAL.get());

        this.race   = stats.getRace();
        skinColor   = visual.getSkinColorRgb()   | 0xFF000000;
        eyeColor    = visual.getEyeColorRgb()    | 0xFF000000;
        hairColor   = visual.getHairColorRgb()   | 0xFF000000;
        detailColor = visual.getDetailColorRgb() | 0xFF000000;
        eyeIndex    = visual.getEyeIndex();
        hairIndex   = visual.getHairIndex();
        mouthIndex  = visual.getMouthIndex();
        noseIndex   = visual.getNoseIndex();
        customSkinColor = visual.isCustomSkinColor();
        skinPreset      = visual.getSkinPreset();

        CustomizationAssets.reload();

        int pl = panelLeft;
        int pt = panelTop;
        int blockTop = pt + IN_Y1 + 6;

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

        blockTop = addField(pl, blockTop, null,
                () -> noseIndex = (noseIndex - 1 + CustomizationAssets.noseCount()) % CustomizationAssets.noseCount(),
                () -> noseIndex = (noseIndex + 1) % CustomizationAssets.noseCount());

        // Zona inferior — calculada dinámicamente
        this.divY        = blockTop + 2;
        this.bottomZoneY = divY + 8;
        this.skinAreaCX  = pl + IN_X1 + PAD + PREVIEW_W + (IN_W - PAD * 2 - PREVIEW_W) / 2;

        buildSkinSection();

        // Botones en la barra inferior de la textura (solo texto, sin fondo gris de vanilla)
        addRenderableWidget(new TextOnlyButton(
                pl + IN_X1, pt + BTN_BAR_Y, BTN_W, 20,
                Component.translatable("screen.zenkai.back"),
                () -> { goingBack = true; if (raceScreen != null) mc.setScreen(raceScreen); else mc.setScreen(null); }));

        addRenderableWidget(new TextOnlyButton(
                pl + IN_X2 - BTN_W, pt + BTN_BAR_Y, BTN_W, 20,
                Component.translatable("screen.zenkai.next"),
                this::goToStyle));
    }

    /** Añade las flechas (y swatch opcional) de un campo apilado y devuelve el Y del siguiente bloque. */
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

    /** Razas con color de piel custom (tinte). El resto usa presets de textura. */
    private boolean isTintRace(Race r) {
        return r == Race.HUMAN || r == Race.SAIYAN || r == Race.MAJIN;
    }

    private int[] presetColorsFor(Race r) {
        return switch (r) {
            case NAMEKIAN -> NAMEKIAN_PRESETS;
            case ARCOSIAN -> ARCOSIAN_PRESETS;
            default       -> SKIN_TONES; // Human / Saiyan / Majin
        };
    }

    /**
     * Sección Skin Color (zona inferior derecha):
     *  · Razas de tinte: paleta de presets de color + swatch "Custom" (picker) + botón "Natural".
     *  · Razas con textura: paleta de presets que fijan skinPreset (sin picker).
     */
    private void buildSkinSection() {
        int[] presets = presetColorsFor(race);
        boolean tint  = isTintRace(race);
        int perRow = 4, gap = 4;
        int total  = presets.length + (tint ? 1 : 0); // +1 = swatch "Custom" en razas de tinte
        int gridY  = bottomZoneY + 12;

        for (int i = 0; i < total; i++) {
            int row = i / perRow, col = i % perRow;
            int countInRow = Math.min(perRow, total - row * perRow);
            int rowW   = countInRow * COLOR_BOX_W + (countInRow - 1) * gap;
            int startX = skinAreaCX - rowW / 2;
            int x = startX + col * (COLOR_BOX_W + gap);
            int y = gridY + row * (COLOR_BOX_H + gap);

            if (tint && i == presets.length) {
                // Swatch "Custom" → abre el picker; muestra el color actual
                addRenderableWidget(new ColorBoxButton(x, y, COLOR_BOX_W, COLOR_BOX_H,
                        () -> skinColor & 0xFFFFFF,
                        () -> activeChannel == ColorChannel.SKIN,
                        () -> togglePicker(ColorChannel.SKIN)));
            } else if (tint) {
                final int c = presets[i];
                addRenderableWidget(new ColorBoxButton(x, y, COLOR_BOX_W, COLOR_BOX_H,
                        () -> c,
                        () -> customSkinColor && (skinColor & 0xFFFFFF) == c,
                        () -> { customSkinColor = true; skinColor = 0xFF000000 | c; closePicker(); applyPreview(); }));
            } else {
                final int idx = i, c = presets[i];
                addRenderableWidget(new ColorBoxButton(x, y, COLOR_BOX_W, COLOR_BOX_H,
                        () -> c,
                        () -> skinPreset == idx,
                        () -> { skinPreset = idx; applyPreview(); }));
            }
        }

        if (tint) {
            int rows = (total + perRow - 1) / perRow;
            int naturalY = gridY + rows * (COLOR_BOX_H + gap) + 2;
            addRenderableWidget(new TextOnlyButton(skinAreaCX - 30, naturalY, 60, 14,
                    Component.literal("Default"),
                    () -> { customSkinColor = false; closePicker(); applyPreview(); })
                    .textColors(0x4A3726, 0x8A6A1E, 0xA0A0A0)); // dark/bronce, legible sobre beige
        }
    }

    private void togglePicker(ColorChannel ch) {
        if (activeChannel == ch && picker != null) { closePicker(); return; }
        openPicker(ch);
    }

    private void openPicker(ColorChannel channel) {
        closePicker();
        activeChannel = channel;
        if (channel == ColorChannel.SKIN) customSkinColor = true; // abrir el picker = modo custom
        String label = switch (channel) {
            case SKIN -> "Skin Color"; case EYE -> "Eye Color";
            case HAIR -> "Hair Color"; case DETAIL -> "Detail Color";
        };
        int pickerX = panelLeft + BG_W + 8;
        if (pickerX + ColorPickerWidget.TOTAL_W > this.width - 4)
            pickerX = panelLeft - ColorPickerWidget.TOTAL_W - 8;
        picker = new ColorPickerWidget(pickerX, panelTop + IN_Y1, colorForChannel(channel), label, argb -> {
            switch (channel) {
                case SKIN -> { skinColor = argb; customSkinColor = true; }
                case EYE -> eyeColor = argb;
                case HAIR -> hairColor = argb; case DETAIL -> detailColor = argb;
            }
            applyPreview();
        });
        addRenderableWidget(picker);
    }

    private void closePicker() {
        if (picker != null) { removeWidget(picker); picker = null; }
        activeChannel = null;
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        int pl = panelLeft;
        int pt = panelTop;

        super.renderBackground(g, mouseX, mouseY, partialTick);
        g.blit(BG, pl, pt, 0, 0, BG_W, BG_H);

        int blockTop = pt + IN_Y1 + 6;
        blockTop = renderField(g, mc, pl, blockTop, "Eyes", CustomizationAssets.eyeLabel(eyeIndex));
        if (race == Race.HUMAN || race == Race.SAIYAN)
            blockTop = renderField(g, mc, pl, blockTop, "Hair", CustomizationAssets.hairLabel(hairIndex));
        blockTop = renderField(g, mc, pl, blockTop, "Mouth", CustomizationAssets.mouthLabel(mouthIndex));
        blockTop = renderField(g, mc, pl, blockTop, "Nose",  CustomizationAssets.noseLabel(noseIndex));

        g.fill(pl + IN_X1 + PAD, divY, pl + IN_X2 - PAD, divY + 1, 0x44FFFFFF);

        // Preview jugador — izquierda zona inferior, dentro del panel
        InventoryScreen.renderEntityInInventoryFollowsMouse(
                g,
                pl + IN_X1 + PAD,              bottomZoneY,
                pl + IN_X1 + PAD + PREVIEW_W,  pt + IN_Y2 - 4,
                PREVIEW_SIZE, 0.0625f,
                (float) mouseX, (float) mouseY, mc.player);

        // Label de la sección de piel — derecha zona inferior
        drawCenteredNoShadow(g, Component.literal(isTintRace(race) ? "Skin Color" : "Skin Preset"),
                skinAreaCX, bottomZoneY, COLOR_SWATCH);

        super.render(g, mouseX, mouseY, partialTick);
    }

    /** Dibuja un campo apilado (título centrado + valor centrado) y devuelve el Y del siguiente bloque. */
    private int renderField(GuiGraphics g, Minecraft mc, int pl, int blockTop, String label, String value) {
        int cx = pl + BG_W / 2;
        drawCenteredNoShadow(g, Component.literal(label), cx, blockTop, COLOR_TITLE);          // título
        g.drawCenteredString(mc.font, Component.literal(value), cx, blockTop + TITLE_H, COLOR_VALUE); // valor
        return blockTop + BLOCK_H;
    }

    /** Texto centrado sin sombra — para colores oscuros que se leen limpios sobre el beige. */
    private void drawCenteredNoShadow(GuiGraphics g, Component text, int cx, int y, int color) {
        var font = Minecraft.getInstance().font;
        g.drawString(font, text, cx - font.width(text) / 2, y, color, false);
    }

    @Override public void renderBackground(@NotNull GuiGraphics g, int mx, int my, float pt) {}

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (picker != null) {
            boolean inside = mx >= picker.getX() && mx < picker.getX() + ColorPickerWidget.TOTAL_W
                    && my >= picker.getY() && my < picker.getY() + ColorPickerWidget.TOTAL_H;
            if (!inside) closePicker();
        }
        return super.mouseClicked(mx, my, button);
    }

    private int colorForChannel(ColorChannel ch) {
        return switch (ch) { case SKIN -> skinColor; case EYE -> eyeColor;
            case HAIR -> hairColor; case DETAIL -> detailColor; };
    }

    private void applyPreview() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        var visual = mc.player.getData(DataAttachments.PLAYER_VISUAL.get());
        visual.setSkinColorRgb(skinColor & 0xFFFFFF);
        visual.setEyeColorRgb(eyeColor & 0xFFFFFF);
        visual.setHairColorRgb(hairColor & 0xFFFFFF);
        visual.setDetailColorRgb(detailColor & 0xFFFFFF);
        visual.setEyeIndex(eyeIndex); visual.setHairIndex(hairIndex);
        visual.setMouthIndex(mouthIndex); visual.setNoseIndex(noseIndex);
        visual.setHairStyleId(hairIndex == 0 ? "hair0" : "hair" + hairIndex);
        visual.setCustomSkinColor(customSkinColor);
        visual.setSkinPreset(skinPreset);
    }

    private void goToStyle() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        goingNext = true;
        mc.setScreen(new StyleSelectionScreen(this, statsSnapshot, visualSnapshot));
    }

    @Override
    public void removed() {
        if (!confirmed && !goingNext && !goingBack) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                var stats  = mc.player.getData(DataAttachments.PLAYER_STATS.get());
                var visual = mc.player.getData(DataAttachments.PLAYER_VISUAL.get());
                if (statsSnapshot  != null) stats.load(statsSnapshot);
                if (visualSnapshot != null) visual.load(visualSnapshot);
            }
        }
        super.removed();
    }

    public void markConfirmed() {
        this.confirmed = true;
        if (raceScreen != null) raceScreen.markConfirmed();
    }

    @Override public boolean isPauseScreen() { return false; }
}