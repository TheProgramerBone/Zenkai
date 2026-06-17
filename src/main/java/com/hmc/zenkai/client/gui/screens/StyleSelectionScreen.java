package com.hmc.zenkai.client.gui.screens;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.gui.buttons.ArrowIconButton;
import com.hmc.zenkai.client.gui.buttons.TextOnlyButton;
import com.hmc.zenkai.client.gui.widgets.ColorBoxButton;
import com.hmc.zenkai.client.gui.widgets.ColorPickerWidget;
import com.hmc.zenkai.core.network.ChooseStylePacket;
import com.hmc.zenkai.core.network.feature.Style;
import com.hmc.zenkai.core.network.feature.stats.ChooseRacePacket;
import com.hmc.zenkai.core.network.feature.stats.DataAttachments;
import com.hmc.zenkai.core.network.feature.race.UpdatePlayerVisualPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class StyleSelectionScreen extends Screen {

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

    private static final int PAD     = 8;
    private static final int ARROW_W = 12;
    private static final int TITLE_H = 11; // separación título → fila de valor

    private static final int COLOR_BOX_W = 20;
    private static final int COLOR_BOX_H = 12;

    // Bloque "Fighting Style" (título arriba, valor entre flechas debajo)
    private static final int S_TITLE_Y = IN_Y1 + 8;
    private static final int S_VALUE_Y = S_TITLE_Y + TITLE_H;
    private static final int DIV1_Y    = S_VALUE_Y + 14;
    private static final int DESC_Y    = DIV1_Y + 6;
    private static final int DIV2_Y    = DESC_Y + 44; // espacio para ~4 líneas de descripción
    private static final int PREVIEW_W = 70;
    private static final int PREVIEW_SIZE = 45;

    // ── Colores de texto ──────────────────────────────────────────────────────
    private static final int COLOR_TITLE  = 0x4A3726; // marrón oscuro → título de campo (Fighting Style)
    private static final int COLOR_VALUE  = 0xFFFFFF; // blanco+sombra → valor seleccionado (Martial Artist, ...)
    private static final int COLOR_DESC   = 0x5A4636; // marrón medio  → cuerpo de la descripción
    private static final int COLOR_SWATCH = 0x8A6A1E; // bronce/dorado → etiqueta de swatch (Ki Color)

    @Nullable private final AppearanceScreen appearanceScreen;
    private final CompoundTag statsSnapshot;
    private final CompoundTag visualSnapshot;

    private boolean confirmed = false;
    private boolean goingBack = false;

    private int leftPos, topPos;
    private final Style[] styles = Style.values();
    private int styleIndex = 0;

    private int kiAreaCX;
    private int kiColor = 0xFF33CCFF;
    private boolean kiPickerOpen = false;
    @Nullable private ColorPickerWidget picker = null;

    public StyleSelectionScreen(@Nullable AppearanceScreen appearanceScreen,
                                @Nullable CompoundTag statsSnapshot,
                                @Nullable CompoundTag visualSnapshot) {
        super(Component.translatable("screen.zenkai.choose_style.title"));
        this.appearanceScreen = appearanceScreen;
        this.statsSnapshot    = statsSnapshot;
        this.visualSnapshot   = visualSnapshot;
    }

    @Override
    protected void init() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        this.clearWidgets();
        this.leftPos = (this.width  - BG_W) / 2;
        this.topPos  = (this.height - BG_H) / 2;

        var visual = mc.player.getData(DataAttachments.PLAYER_VISUAL.get());
        var stats  = mc.player.getData(DataAttachments.PLAYER_STATS.get());

        kiColor = visual.getAuraColorRgb() | 0xFF000000;

        Style cur = stats.getStyle();
        for (int i = 0; i < styles.length; i++) {
            if (styles[i] == cur) { styleIndex = i; break; }
        }

        int lp = leftPos;
        int tp = topPos;

        // Flechas de estilo — a los lados de la fila de valor
        int arrowY = tp + S_VALUE_Y - 2;
        addRenderableWidget(new ArrowIconButton(
                lp + IN_X1 + PAD, arrowY,
                ArrowIconButton.Dir.LEFT,
                () -> styleIndex = (styleIndex - 1 + styles.length) % styles.length));
        addRenderableWidget(new ArrowIconButton(
                lp + IN_X2 - PAD - ARROW_W, arrowY,
                ArrowIconButton.Dir.RIGHT,
                () -> styleIndex = (styleIndex + 1) % styles.length));

        // Swatch Ki Color — a la derecha del preview (patrón de AppearanceScreen)
        this.kiAreaCX = lp + IN_X1 + PAD + PREVIEW_W + (IN_W - PAD * 2 - PREVIEW_W) / 2;
        int kiBoxX = kiAreaCX - COLOR_BOX_W / 2;
        int kiBoxY = tp + DIV2_Y + 8 + 14;
        addRenderableWidget(new ColorBoxButton(kiBoxX, kiBoxY, COLOR_BOX_W, COLOR_BOX_H,
                () -> kiColor & 0xFFFFFF, () -> kiPickerOpen, this::toggleKiPicker));

        // Botones en la barra inferior (solo texto, sin fondo gris de vanilla)
        addRenderableWidget(new TextOnlyButton(
                lp + IN_X1, tp + BTN_BAR_Y, BTN_W, 20,
                Component.translatable("screen.zenkai.back"),
                TEX_BTN, null,
                () -> { goingBack = true; if (appearanceScreen != null) mc.setScreen(appearanceScreen); else mc.setScreen(null); }));

        addRenderableWidget(new TextOnlyButton(
                lp + IN_X2 - BTN_W, tp + BTN_BAR_Y, BTN_W, 20,
                Component.translatable("screen.zenkai.confirm"),
                TEX_BTN, null,          // ← textura normal, y null = sin versión hover
                this::onConfirm));
    }

    private void toggleKiPicker() {
        if (kiPickerOpen && picker != null) { closeKiPicker(); return; }
        openKiPicker();
    }

    private void openKiPicker() {
        closeKiPicker();
        kiPickerOpen = true;
        int pickerX = leftPos + BG_W + 8;
        if (pickerX + ColorPickerWidget.TOTAL_W > this.width - 4)
            pickerX = leftPos - ColorPickerWidget.TOTAL_W - 8;
        picker = new ColorPickerWidget(pickerX, topPos + IN_Y1, kiColor, "Ki Color", argb -> {
            kiColor = argb;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null)
                mc.player.getData(DataAttachments.PLAYER_VISUAL.get()).setAuraColorRgb(argb & 0xFFFFFF);
        });
        addRenderableWidget(picker);
    }

    private void closeKiPicker() {
        if (picker != null) { removeWidget(picker); picker = null; }
        kiPickerOpen = false;
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        Style s = styles[styleIndex];
        String styleKey = "screen.zenkai.style." + s.name().toLowerCase();
        int lp = leftPos;
        int tp = topPos;
        int cx = lp + BG_W / 2;

        super.renderBackground(g, mouseX, mouseY, partialTick);
        g.blit(BG, lp, tp, 0, 0, BG_W, BG_H);

        // Bloque estilo — título arriba, valor (entre flechas) debajo
        drawCenteredNoShadow(g, Component.translatable("screen.zenkai.label.style"),
                cx, tp + S_TITLE_Y, COLOR_TITLE);
        g.drawCenteredString(mc.font, Component.translatable(styleKey),
                cx, tp + S_VALUE_Y, COLOR_VALUE);

        g.fill(lp + IN_X1 + PAD, tp + DIV1_Y, lp + IN_X2 - PAD, tp + DIV1_Y + 1, 0x44FFFFFF);

        // Descripción
        String[] lines = wrapText(Component.translatable(styleKey + ".desc").getString(),
                mc.font, IN_W - PAD * 2);
        for (int i = 0; i < lines.length; i++) {
            g.drawString(mc.font, Component.literal(lines[i]),
                    lp + IN_X1 + PAD, tp + DESC_Y + i * 11, COLOR_DESC, false);
        }

        g.fill(lp + IN_X1 + PAD, tp + DIV2_Y, lp + IN_X2 - PAD, tp + DIV2_Y + 1, 0x44FFFFFF);

        // Preview jugador — izquierda zona inferior
        InventoryScreen.renderEntityInInventoryFollowsMouse(
                g,
                lp + IN_X1 + PAD,              tp + DIV2_Y + 8,
                lp + IN_X1 + PAD + PREVIEW_W,  tp + IN_Y2 - 4,
                PREVIEW_SIZE, 0.0625f,
                (float) mouseX, (float) mouseY, mc.player);

        // Label "Ki Color" encima del swatch
        drawCenteredNoShadow(g, Component.literal("Ki Color"), kiAreaCX, tp + DIV2_Y + 8, COLOR_SWATCH);

        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override public void renderBackground(@NotNull GuiGraphics g, int mx, int my, float pt) {}

    /** Texto centrado sin sombra — para colores oscuros que se leen limpios sobre el beige. */
    private void drawCenteredNoShadow(GuiGraphics g, Component text, int cx, int y, int color) {
        var font = Minecraft.getInstance().font;
        g.drawString(font, text, cx - font.width(text) / 2, y, color, false);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (picker != null) {
            boolean inside = mx >= picker.getX() && mx < picker.getX() + ColorPickerWidget.TOTAL_W
                    && my >= picker.getY() && my < picker.getY() + ColorPickerWidget.TOTAL_H;
            if (!inside) closeKiPicker();
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public void removed() {
        if (!confirmed && !goingBack) {
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

    private void onConfirm() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        var stats  = mc.player.getData(DataAttachments.PLAYER_STATS.get());
        var visual = mc.player.getData(DataAttachments.PLAYER_VISUAL.get());
        visual.setAuraColorRgb(kiColor & 0xFFFFFF);
        PacketDistributor.sendToServer(new ChooseRacePacket(stats.getRace()));
        PacketDistributor.sendToServer(UpdatePlayerVisualPacket.from(visual));
        PacketDistributor.sendToServer(new ChooseStylePacket(styles[styleIndex]));
        confirmed = true;
        if (appearanceScreen != null) appearanceScreen.markConfirmed();
        mc.setScreen(null);
    }

    private String[] wrapText(String text, net.minecraft.client.gui.Font font, int maxWidth) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        String[] words = text.split(" ");
        StringBuilder line = new StringBuilder();
        for (String w : words) {
            String test = line.isEmpty() ? w : line + " " + w;
            if (font.width(test) > maxWidth && !line.isEmpty()) {
                lines.add(line.toString()); line = new StringBuilder(w);
            } else line = new StringBuilder(test);
        }
        if (!line.isEmpty()) lines.add(line.toString());
        return lines.toArray(new String[0]);
    }

    @Override public boolean isPauseScreen() { return false; }
}