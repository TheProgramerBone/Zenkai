package com.hmc.zenkai.client.gui.screens;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.customization.CustomizationAssets;
import com.hmc.zenkai.client.gui.buttons.ArrowIconButton;
import com.hmc.zenkai.client.gui.buttons.TextOnlyButton;
import com.hmc.zenkai.client.gui.widgets.ColorBoxButton;
import com.hmc.zenkai.client.gui.widgets.ColorPickerWidget;
import com.hmc.zenkai.core.network.feature.Race;
import com.hmc.zenkai.core.network.feature.player.PlayerVisualAttachment;
import com.hmc.zenkai.core.network.feature.race.GeoLayerArmorItem;
import com.hmc.zenkai.core.network.feature.race.RaceLayerDiscovery;
import com.hmc.zenkai.core.network.feature.race.RaceSkinSlots;
import com.hmc.zenkai.core.network.feature.stats.DataAttachments;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
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

    private static final ResourceLocation TEX_BTN =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/btn_wide.png");

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
    private static final int TITLE_H      = 11;
    private static final int BLOCK_H      = 27;
    private static final int COLOR_BOX_W  = 20;
    private static final int COLOR_BOX_H  = 12;

    private static final int SKIN_SECTION_DY = 12;

    // Paso vertical base entre filas de capas (label + presets). Cada capa puede sumarle
    // su propio "dy" desde el JSON para ajuste fino de posición.
    private static final int LAYER_DY = 24;

    private static final int ARROW_LX            = IN_X1 + PAD;
    private static final int ARROW_RX_NO_COLOR   = IN_X2 - PAD - ARROW_W;
    private static final int ARROW_RX_WITH_COLOR = IN_X2 - PAD - ARROW_W - COLOR_BOX_W - 4;

    private static final int PREVIEW_W    = 80;
    private static final int PREVIEW_SIZE = 45;



    private static final int COLOR_TITLE  = 0x4A3726;
    private static final int COLOR_VALUE  = 0xFFFFFF;
    private static final int COLOR_SWATCH = 0x4A3726;

    // Tonos de piel Human/Saiyan/Majin. Las razas multicolor sacan TODO de los JSON de capa.
    private static final int[] SKIN_TONES = { 0xF5C7AC, 0xEAB58E, 0xD5A07A, 0xC68642, 0x8D5524, 0x5C3A21 };

    @Nullable private final RaceSelectionScreen raceScreen;
    private final CompoundTag statsSnapshot;
    private final CompoundTag visualSnapshot;

    private boolean confirmed = false, goingBack = false, goingNext = false;
    private int panelLeft, panelTop;
    private int divY, bottomZoneY, skinAreaCX;

    private int eyeIndex = 0, hairIndex = 0, mouthIndex = 0, noseIndex = 0;
    private int skinColor = 0xFFD5A07A, eyeColor = 0xFF2E86C1;
    private int hairColor = 0xFF1A1A1A;
    private boolean customSkinColor = false;
    private int     skinPreset      = 0;
    private boolean genderFemale    = false;
    private boolean showGender       = false;
    private int     genderTitleY, genderValueY;

    // ── Estado genérico de capas de tinte (razas multicolor) ──────────────────
    // index 0 = piel (escribe skinColorRgb); index >= 1 = capas (escriben layerColors[index]).
    private final java.util.List<RaceLayerDiscovery.Layer> tintLayers = new java.util.ArrayList<>();
    private final java.util.Map<Integer, Integer> layerArgb   = new java.util.HashMap<>(); // index -> ARGB actual
    private final java.util.Map<Integer, Integer> layerLabelY = new java.util.HashMap<>(); // index -> Y de la etiqueta
    private int activeLayerIndex = -1; // capa con picker abierto (-1 = ninguna)

    private enum ColorChannel { SKIN, EYE, HAIR }
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
        eyeIndex    = visual.getEyeIndex();
        hairIndex   = visual.getHairIndex();
        mouthIndex  = visual.getMouthIndex();
        noseIndex   = visual.getNoseIndex();
        customSkinColor = visual.isCustomSkinColor();
        skinPreset      = visual.getSkinPreset();
        genderFemale    = visual.getGender() == PlayerVisualAttachment.Gender.FEMALE;

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

        this.divY        = blockTop + 2;
        this.bottomZoneY = divY + 8;
        this.skinAreaCX  = 25 + pl + IN_X1 + PAD + PREVIEW_W + (IN_W - PAD * 2 - PREVIEW_W) / 2;

        buildSkinSection();

        addRenderableWidget(new TextOnlyButton(
                pl + IN_X1, pt + BTN_BAR_Y, BTN_W, 20,
                Component.translatable("screen.zenkai.back"),
                TEX_BTN, null,
                () -> { goingBack = true; if (raceScreen != null) mc.setScreen(raceScreen); else mc.setScreen(null); }));

        addRenderableWidget(new TextOnlyButton(
                pl + IN_X2 - BTN_W, pt + BTN_BAR_Y, BTN_W, 20,
                Component.translatable("screen.zenkai.next"),
                TEX_BTN, null,
                this::goToStyle));
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

    private boolean isTintRace(Race r) {
        return r == Race.HUMAN || r == Race.SAIYAN || r == Race.MAJIN;
    }

    private boolean isMultiTintRace(Race r) {
        return r == Race.NAMEKIAN || r == Race.ARCOSIAN;
    }

    private void buildSkinSection() {
        if (isMultiTintRace(race)) { buildLayerSections(); return; }

        int[] presets = SKIN_TONES;
        boolean tint  = isTintRace(race);
        int perRow = 4, gap = 4;
        int total  = presets.length + (tint ? 1 : 0);
        int gridY  = bottomZoneY + SKIN_SECTION_DY + 12;

        for (int i = 0; i < total; i++) {
            int row = i / perRow, col = i % perRow;
            int countInRow = Math.min(perRow, total - row * perRow);
            int rowW   = countInRow * COLOR_BOX_W + (countInRow - 1) * gap;
            int startX = skinAreaCX - rowW / 2;
            int x = startX + col * (COLOR_BOX_W + gap);
            int y = gridY + row * (COLOR_BOX_H + gap);

            if (tint && i == presets.length) {
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

        showGender = tint;
        if (tint) {
            int rows = (total + perRow - 1) / perRow;
            int naturalY = gridY + rows * (COLOR_BOX_H + gap) - 2;
            addRenderableWidget(new TextOnlyButton(skinAreaCX - 30, naturalY, 60, 14,
                    Component.literal("Default"),
                    () -> { customSkinColor = false; closePicker(); applyPreview(); })
                    .textColors(0xFFFFFF, 0xFFF149, 0xA0A0A0));

            genderTitleY = naturalY + 16;
            genderValueY = genderTitleY + 11;
            int gw = 84;
            int arrowY = genderValueY - 1;
            addRenderableWidget(new ArrowIconButton((skinAreaCX - gw / 2), arrowY,
                    ArrowIconButton.Dir.LEFT,  () -> { genderFemale = !genderFemale; applyPreview(); }));
            addRenderableWidget(new ArrowIconButton((skinAreaCX + gw / 2 - ARROW_W), arrowY,
                    ArrowIconButton.Dir.RIGHT, () -> { genderFemale = !genderFemale; applyPreview(); }));
        }
    }

    /**
     * Razas multicolor: una fila de color por CAPA descubierta (genérico, data-driven).
     * index 0 = piel (escribe skinColorRgb); index >= 1 = capa (escribe layerColors[index]).
     * Etiqueta y presets salen del JSON de cada capa. Añadir/quitar capas = soltar PNG+JSON.
     */
    private void buildLayerSections() {
        showGender = false;
        customSkinColor = true; // multicolor siempre coloreable
        tintLayers.clear();
        layerArgb.clear();
        layerLabelY.clear();

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        var visual = mc.player.getData(DataAttachments.PLAYER_VISUAL.get());

        ItemStack body = RaceSkinSlots.getVirtualRaceArmor(mc.player, EquipmentSlot.CHEST);
        if (!(body.getItem() instanceof GeoLayerArmorItem gi)) return;

        int startY = bottomZoneY + SKIN_SECTION_DY + 12;
        int row = 0;
        for (RaceLayerDiscovery.Layer L : RaceLayerDiscovery.layersFor(gi)) {
            int idx = -10+L.index();
            int cur = (idx == 0)
                    ? visual.getSkinColorRgb()
                    : (visual.hasLayerColor(idx) ? visual.getLayerColorRgb(idx) : L.defaultRgb());
            layerArgb.put(idx, 0xFF000000 | (cur & 0xFFFFFF));

            int labelY = -30 + startY + row * LAYER_DY + L.dy();
            layerLabelY.put(idx, labelY);
            tintLayers.add(L);
            buildLayerRow(L, labelY + 10);
            row++;
        }
    }

    private void buildLayerRow(RaceLayerDiscovery.Layer L, int y) {
        int[] presets = L.presets();
        int gap = 4;
        int total = presets.length + 1; // + swatch/picker "custom"
        int rowW   = total * COLOR_BOX_W + (total - 1) * gap;
        int startX = (skinAreaCX - rowW / 2);
        for (int i = 0; i < total; i++) {
            int x = startX + i * (COLOR_BOX_W + gap);
            if (i == presets.length) {
                addRenderableWidget(new ColorBoxButton(x, y, COLOR_BOX_W, COLOR_BOX_H,
                        () -> layerCurrent(L) & 0xFFFFFF,
                        () -> activeLayerIndex == L.index(),
                        () -> toggleLayerPicker(L)));
            } else {
                final int c = presets[i];
                addRenderableWidget(new ColorBoxButton(x, y, COLOR_BOX_W, COLOR_BOX_H,
                        () -> c,
                        () -> (layerCurrent(L) & 0xFFFFFF) == c,
                        () -> { layerArgb.put(L.index(), 0xFF000000 | c); closePicker(); applyPreview(); }));
            }
        }
    }

    /** ARGB actual de una capa (o su default si aún no se tocó). */
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

    private void togglePicker(ColorChannel ch) {
        if (activeChannel == ch && picker != null) { closePicker(); return; }
        openPicker(ch);
    }

    private void openPicker(ColorChannel channel) {
        closePicker();
        activeChannel = channel;
        if (channel == ColorChannel.SKIN) customSkinColor = true;
        String label = switch (channel) {
            case SKIN -> "Skin Color"; case EYE -> "Eye Color";
            case HAIR -> "Hair Color";
        };
        int pickerX = panelLeft + BG_W + 8;
        if (pickerX + ColorPickerWidget.TOTAL_W > this.width - 4)
            pickerX = panelLeft - ColorPickerWidget.TOTAL_W - 8;
        picker = new ColorPickerWidget(pickerX, panelTop + IN_Y1, colorForChannel(channel), label, argb -> {
            switch (channel) {
                case SKIN -> { skinColor = argb; customSkinColor = true; }
                case EYE -> eyeColor = argb;
                case HAIR -> hairColor = argb;
            }
            applyPreview();
        });
        addRenderableWidget(picker);
    }

    private void closePicker() {
        if (picker != null) { removeWidget(picker); picker = null; }
        activeChannel = null;
        activeLayerIndex = -1;
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

        InventoryScreen.renderEntityInInventoryFollowsMouse(
                g,
                pl + IN_X1 + PAD,              bottomZoneY,
                pl + IN_X1 + PAD + PREVIEW_W,  pt + IN_Y2 - 4,
                PREVIEW_SIZE, 0.0625f,
                (float) mouseX, (float) mouseY, mc.player);

        if (isMultiTintRace(race)) {
            for (RaceLayerDiscovery.Layer L : tintLayers) {
                Integer ly = layerLabelY.get(L.index());
                if (ly != null) drawCenteredNoShadow(g, L.labelComponent(), skinAreaCX, ly, COLOR_SWATCH);
            }
        } else {
            drawCenteredNoShadow(g, Component.literal(isTintRace(race) ? "Skin Color" : "Skin Preset"),
                    skinAreaCX, bottomZoneY + SKIN_SECTION_DY, COLOR_SWATCH);
        }

        if (showGender) {
            drawCenteredNoShadow(g, Component.literal("Gender"), skinAreaCX, genderTitleY, COLOR_SWATCH);
            g.drawCenteredString(mc.font, Component.literal(genderFemale ? "Female" : "Male"),
                    skinAreaCX, genderValueY, COLOR_VALUE);
        }

        super.render(g, mouseX, mouseY, partialTick);
    }

    private int renderField(GuiGraphics g, Minecraft mc, int pl, int blockTop, String label, String value) {
        int cx = pl + BG_W / 2;
        drawCenteredNoShadow(g, Component.literal(label), cx, blockTop, COLOR_TITLE);
        g.drawCenteredString(mc.font, Component.literal(value), cx, blockTop + TITLE_H, COLOR_VALUE);
        return blockTop + BLOCK_H;
    }

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
        return switch (ch) {
            case SKIN -> skinColor; case EYE -> eyeColor;
            case HAIR -> hairColor;
        };
    }

    private void applyPreview() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        var visual = mc.player.getData(DataAttachments.PLAYER_VISUAL.get());
        visual.setSkinColorRgb(skinColor & 0xFFFFFF);
        visual.setEyeColorRgb(eyeColor & 0xFFFFFF);
        visual.setHairColorRgb(hairColor & 0xFFFFFF);
        visual.setEyeIndex(eyeIndex); visual.setHairIndex(hairIndex);
        visual.setMouthIndex(mouthIndex); visual.setNoseIndex(noseIndex);
        visual.setHairStyleId(hairIndex == 0 ? "hair0" : "hair" + hairIndex);
        visual.setCustomSkinColor(customSkinColor);
        visual.setSkinPreset(skinPreset);
        visual.setGender(genderFemale ? PlayerVisualAttachment.Gender.FEMALE
                : PlayerVisualAttachment.Gender.MALE);

        // Razas multicolor: capa 0 -> piel (skinColorRgb); capa >=1 -> layerColors[index].
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