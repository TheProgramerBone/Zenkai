package com.hmc.zenkai.client.gui.screens;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.customization.CustomizationAssets;
import com.hmc.zenkai.client.gui.buttons.ArrowIconButton;
import com.hmc.zenkai.client.gui.widgets.ColorBoxButton;
import com.hmc.zenkai.client.gui.widgets.ColorPickerWidget;
import com.hmc.zenkai.core.network.feature.Race;
import com.hmc.zenkai.core.network.feature.stats.DataAttachments;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AppearanceScreen extends Screen {

    // ── Assets ───────────────────────────────────────────────────────────────
    private static final ResourceLocation BG =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/common_screen.png");
    private static final int BG_W = 256;
    private static final int BG_H = 256;

    // ── Layout filas superiores ───────────────────────────────────────────────
    private static final int PAD         = 10;
    private static final int ROW_H       = 22;
    private static final int LABEL_Y_OFF = 6;
    private static final int COLOR_BOX_W = 20;
    private static final int COLOR_BOX_H = 12;

    private static final int ARROW_LEFT_X             = PAD + 2;
    private static final int ARROW_RIGHT_X_NO_COLOR   = BG_W - PAD - 14;
    private static final int ARROW_RIGHT_X_WITH_COLOR = BG_W - PAD - 14 - COLOR_BOX_W - 6;
    private static final int VALUE_CENTER_WITH_COLOR  = (ARROW_LEFT_X + 14 + ARROW_RIGHT_X_WITH_COLOR) / 2;
    private static final int VALUE_CENTER_NO_COLOR    = (ARROW_LEFT_X + 14 + ARROW_RIGHT_X_NO_COLOR)   / 2;

    // ── Layout zona inferior (preview + skin color) ───────────────────────────
    // El preview ocupa la mitad izquierda, skin color la mitad derecha
    private static final int BOTTOM_ZONE_H  = 80;  // alto de la zona inferior
    private static final int PREVIEW_W      = 70;  // ancho de la zona de preview
    private static final int PREVIEW_SIZE   = 40;

    // ── Colores UI ────────────────────────────────────────────────────────────
    private static final int COLOR_LABEL = 0xC8A96E;
    private static final int COLOR_VALUE = 0xFFFFFF;

    // ── Navegación ────────────────────────────────────────────────────────────
    @Nullable private final RaceSelectionScreen raceScreen;
    private final CompoundTag statsSnapshot;
    private final CompoundTag visualSnapshot;

    private boolean confirmed = false;
    private boolean goingBack = false;
    private boolean goingNext = false;

    private int panelLeft, panelTop;

    // ── Estado ───────────────────────────────────────────────────────────────
    private int eyeIndex   = 0;
    private int hairIndex  = 0;
    private int mouthIndex = 0;
    private int noseIndex  = 0;

    private int skinColor   = 0xFFD5A07A;
    private int eyeColor    = 0xFF2E86C1;
    private int hairColor   = 0xFF1A1A1A;
    private int detailColor = 0xFF9B59B6;

    // ── Picker ───────────────────────────────────────────────────────────────
    private enum ColorChannel { SKIN, EYE, HAIR, DETAIL }
    @Nullable private ColorChannel    activeChannel = null;
    @Nullable private ColorPickerWidget picker      = null;

    private Race race = Race.HUMAN;

    public AppearanceScreen(
            @Nullable RaceSelectionScreen raceScreen,
            @Nullable CompoundTag statsSnapshot,
            @Nullable CompoundTag visualSnapshot
    ) {
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

        CustomizationAssets.reload();

        int pl  = panelLeft;
        int pt  = panelTop;
        int rowY = pt + 12;

        // ── Filas superiores ──────────────────────────────────────────────────
        addSelectorRow(pl, rowY, ColorChannel.EYE,
                () -> eyeIndex = (eyeIndex - 1 + CustomizationAssets.eyesCount()) % CustomizationAssets.eyesCount(),
                () -> eyeIndex = (eyeIndex + 1) % CustomizationAssets.eyesCount());
        rowY += ROW_H;

        if (race == Race.HUMAN || race == Race.SAIYAN) {
            addSelectorRow(pl, rowY, ColorChannel.HAIR,
                    () -> hairIndex = (hairIndex - 1 + CustomizationAssets.hairCount()) % CustomizationAssets.hairCount(),
                    () -> hairIndex = (hairIndex + 1) % CustomizationAssets.hairCount());
            rowY += ROW_H;
        }

        addSelectorRow(pl, rowY, null,
                () -> mouthIndex = (mouthIndex - 1 + CustomizationAssets.mouthCount()) % CustomizationAssets.mouthCount(),
                () -> mouthIndex = (mouthIndex + 1) % CustomizationAssets.mouthCount());
        rowY += ROW_H;

        addSelectorRow(pl, rowY, null,
                () -> noseIndex = (noseIndex - 1 + CustomizationAssets.noseCount()) % CustomizationAssets.noseCount(),
                () -> noseIndex = (noseIndex + 1) % CustomizationAssets.noseCount());
        rowY += ROW_H;

        // ── Divisor entre filas y zona inferior ───────────────────────────────
        // (solo visual, no widget)

        // ── Zona inferior: Skin Color a la derecha del preview ────────────────
        // El preview ocupa [pl+PAD .. pl+PAD+PREVIEW_W]
        // Skin color va centrado en [pl+PAD+PREVIEW_W .. pl+BG_W-PAD]
        int bottomZoneY   = pt + BG_H - BOTTOM_ZONE_H - 30; // Y de inicio de zona inferior
        int skinAreaLeft  = pl + PAD + PREVIEW_W + 8;
        int skinAreaRight = pl + BG_W - PAD;
        int skinAreaCX    = (skinAreaLeft + skinAreaRight) / 2;

        // ColorBoxButton de skin — centrado verticalmente en la zona inferior
        int skinBoxX = skinAreaCX - COLOR_BOX_W / 2;
        int skinBoxY = bottomZoneY + (BOTTOM_ZONE_H - COLOR_BOX_H) / 2 + 8; // +8 para dejar espacio al label

        addRenderableWidget(new ColorBoxButton(
                skinBoxX, skinBoxY, COLOR_BOX_W, COLOR_BOX_H,
                () -> skinColor & 0xFFFFFF,
                () -> activeChannel == ColorChannel.SKIN,
                () -> togglePicker(ColorChannel.SKIN)
        ));

        // ── Detail color (Arcosian) — debajo del skin color ───────────────────
        if (race == Race.ARCOSIAN) {
            addRenderableWidget(new ColorBoxButton(
                    skinBoxX, skinBoxY + ROW_H, COLOR_BOX_W, COLOR_BOX_H,
                    () -> detailColor & 0xFFFFFF,
                    () -> activeChannel == ColorChannel.DETAIL,
                    () -> togglePicker(ColorChannel.DETAIL)
            ));
        }

        // ── Botones ───────────────────────────────────────────────────────────
        addRenderableWidget(Button.builder(
                Component.translatable("screen.zenkai.back"),
                b -> { goingBack = true; if (raceScreen != null) mc.setScreen(raceScreen); else mc.setScreen(null); }
        ).bounds(pl + PAD, pt + BG_H - 26, 50, 20).build());

        addRenderableWidget(Button.builder(
                Component.translatable("screen.zenkai.next"),
                b -> goToStyle()
        ).bounds(pl + BG_W - PAD - 52, pt + BG_H - 26, 52, 20).build());
    }

    // ── Construcción de filas superiores ──────────────────────────────────────

    private void addSelectorRow(int pl, int rowY,
                                @Nullable ColorChannel channel,
                                Runnable onLeft, Runnable onRight) {
        addRenderableWidget(new ArrowIconButton(
                pl + ARROW_LEFT_X, rowY + (ROW_H - 11) / 2,
                ArrowIconButton.Dir.LEFT, () -> { onLeft.run(); applyPreview(); }));

        int rightX = (channel != null) ? pl + ARROW_RIGHT_X_WITH_COLOR : pl + ARROW_RIGHT_X_NO_COLOR;
        addRenderableWidget(new ArrowIconButton(
                rightX, rowY + (ROW_H - 11) / 2,
                ArrowIconButton.Dir.RIGHT, () -> { onRight.run(); applyPreview(); }));

        if (channel != null) {
            final ColorChannel ch = channel;
            int bx = pl + BG_W - PAD - COLOR_BOX_W;
            int by = rowY + (ROW_H - COLOR_BOX_H) / 2;
            addRenderableWidget(new ColorBoxButton(
                    bx, by, COLOR_BOX_W, COLOR_BOX_H,
                    () -> colorForChannel(ch) & 0xFFFFFF,
                    () -> activeChannel == ch,
                    () -> togglePicker(ch)
            ));
        }
    }

    // ── Picker flotante ───────────────────────────────────────────────────────

    private void togglePicker(ColorChannel channel) {
        if (activeChannel == channel && picker != null) { closePicker(); return; }
        openPicker(channel);
    }

    private void openPicker(ColorChannel channel) {
        closePicker();
        activeChannel = channel;

        int initialColor = colorForChannel(channel);
        String label = switch (channel) {
            case SKIN   -> "Skin Color";
            case EYE    -> "Eye Color";
            case HAIR   -> "Hair Color";
            case DETAIL -> "Detail Color";
        };

        int pickerX = panelLeft + BG_W + 8;
        if (pickerX + ColorPickerWidget.TOTAL_W > this.width - 4)
            pickerX = panelLeft - ColorPickerWidget.TOTAL_W - 8;
        int pickerY = panelTop + 20;

        picker = new ColorPickerWidget(pickerX, pickerY, initialColor, label, argb -> {
            switch (channel) {
                case SKIN   -> skinColor   = argb;
                case EYE    -> eyeColor    = argb;
                case HAIR   -> hairColor   = argb;
                case DETAIL -> detailColor = argb;
            }
            applyPreview();
        });
        addRenderableWidget(picker);
    }

    private void closePicker() {
        if (picker != null) { removeWidget(picker); picker = null; }
        activeChannel = null;
    }

    // ── Render ────────────────────────────────────────────────────────────────

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        int pl = panelLeft;
        int pt = panelTop;

        // 1) Overlay + panel
        super.renderBackground(g, mouseX, mouseY, partialTick);
        g.blit(BG, pl, pt, 0, 0, BG_W, BG_H);

        // 2) Filas superiores
        int rowY = pt + 12;

        rowY = renderSelectorRow(g, mc, pl, rowY, "Eyes",
                CustomizationAssets.eyeLabel(eyeIndex), ColorChannel.EYE);

        if (race == Race.HUMAN || race == Race.SAIYAN) {
            rowY = renderSelectorRow(g, mc, pl, rowY, "Hair",
                    CustomizationAssets.hairLabel(hairIndex), ColorChannel.HAIR);
        }

        rowY = renderSelectorRow(g, mc, pl, rowY, "Mouth",
                CustomizationAssets.mouthLabel(mouthIndex), null);

        rowY = renderSelectorRow(g, mc, pl, rowY, "Nose",
                CustomizationAssets.noseLabel(noseIndex), null);

        // 3) Divisor entre filas y zona inferior
        int divY = rowY + 4;
        g.fill(pl + PAD, divY, pl + BG_W - PAD, divY + 1, 0x44FFFFFF);

        // 4) Zona inferior
        int bottomZoneY  = pt + BG_H - BOTTOM_ZONE_H - 30;
        int skinAreaLeft = pl + PAD + PREVIEW_W + 8;
        int skinAreaCX   = (skinAreaLeft + pl + BG_W - PAD) / 2;

        // Preview del jugador — mitad izquierda
        InventoryScreen.renderEntityInInventoryFollowsMouse(
                g,
                pl + PAD,            bottomZoneY,
                pl + PAD + PREVIEW_W, bottomZoneY + BOTTOM_ZONE_H,
                PREVIEW_SIZE, 0.0625f,
                (float) mouseX, (float) mouseY,
                mc.player
        );

        // Skin Color label + box — mitad derecha, centrado verticalmente
        int skinLabelY = bottomZoneY + (BOTTOM_ZONE_H / 2) - 14;
        g.drawCenteredString(mc.font, Component.literal("Skin Color"),
                skinAreaCX, skinLabelY, COLOR_LABEL);

        // (el ColorBoxButton ya se renderiza solo como widget)

        // Detail Color label (Arcosian)
        if (race == Race.ARCOSIAN) {
            g.drawCenteredString(mc.font, Component.literal("Detail Color"),
                    skinAreaCX, skinLabelY + ROW_H, COLOR_LABEL);
        }

        // 5) Widgets encima
        super.render(g, mouseX, mouseY, partialTick);
    }

    private int renderSelectorRow(GuiGraphics g, Minecraft mc,
                                  int pl, int rowY,
                                  String label, String value,
                                  @Nullable ColorChannel channel) {
        g.drawString(mc.font, Component.literal(label),
                pl + ARROW_LEFT_X + 16, rowY + LABEL_Y_OFF, COLOR_LABEL, false);

        int centerX = pl + (channel != null ? VALUE_CENTER_WITH_COLOR : VALUE_CENTER_NO_COLOR);
        g.drawCenteredString(mc.font, Component.literal(value),
                centerX, rowY + LABEL_Y_OFF, COLOR_VALUE);

        return rowY + ROW_H;
    }

    @Override
    public void renderBackground(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {}

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (picker != null) {
            boolean inside = mx >= picker.getX()
                    && mx < picker.getX() + ColorPickerWidget.TOTAL_W
                    && my >= picker.getY()
                    && my < picker.getY() + ColorPickerWidget.TOTAL_H;
            if (!inside) closePicker();
        }
        return super.mouseClicked(mx, my, button);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private int colorForChannel(ColorChannel channel) {
        return switch (channel) {
            case SKIN   -> skinColor;
            case EYE    -> eyeColor;
            case HAIR   -> hairColor;
            case DETAIL -> detailColor;
        };
    }

    private void applyPreview() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        var visual = mc.player.getData(DataAttachments.PLAYER_VISUAL.get());
        visual.setSkinColorRgb(skinColor   & 0xFFFFFF);
        visual.setEyeColorRgb(eyeColor     & 0xFFFFFF);
        visual.setHairColorRgb(hairColor   & 0xFFFFFF);
        visual.setDetailColorRgb(detailColor & 0xFFFFFF);
        visual.setEyeIndex(eyeIndex);
        visual.setHairIndex(hairIndex);
        visual.setMouthIndex(mouthIndex);
        visual.setNoseIndex(noseIndex);
        // Sincronizar hairStyleId para HairResolver
        visual.setHairStyleId(hairIndex == 0 ? "hair0" : "hair" + hairIndex);
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

    @Override
    public boolean isPauseScreen() { return false; }
}