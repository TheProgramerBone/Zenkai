package com.hmc.zenkai.client.gui.screens;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.gui.buttons.ArrowIconButton;
import com.hmc.zenkai.core.network.ChooseStylePacket;
import com.hmc.zenkai.core.network.feature.Style;
import com.hmc.zenkai.core.network.feature.stats.ChooseRacePacket;
import com.hmc.zenkai.core.network.feature.stats.DataAttachments;
import com.hmc.zenkai.core.network.feature.race.UpdatePlayerVisualPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
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
    private static final int BG_W = 256;
    private static final int BG_H = 256;

    private static final int PAD         = 10;
    private static final int ROW_STYLE_Y = 14;
    private static final int DIV1_Y      = 34;
    private static final int DESC_Y      = 40;
    private static final int DIV2_Y      = 110;
    private static final int COLOR_LBL_Y = 118;
    private static final int HEX_LBL_Y  = 134;
    private static final int HEX_BOX_Y  = 144;
    private static final int COLOR_BOX_W = 55;
    private static final int COLOR_BOX_H = 48;
    private static final int PREVIEW_SIZE = 30;

    private static final int COLOR_LABEL = 0xC8A96E;
    private static final int COLOR_VALUE = 0xFFDDAA;
    private static final int COLOR_DESC  = 0xCCCCCC;

    @Nullable private final RaceAppearanceScreen raceScreen;
    private final CompoundTag statsSnapshot;
    private final CompoundTag visualSnapshot;

    private boolean confirmed = false;
    private boolean goingBack = false;

    private int leftPos, topPos;

    private final Style[] styles = Style.values();
    private int styleIndex = 0;

    private EditBox hexBox;
    private int rgbValue = 0x33CCFF;

    public StyleSelectionScreen(
            @Nullable RaceAppearanceScreen raceScreen,
            @Nullable CompoundTag statsSnapshot,
            @Nullable CompoundTag visualSnapshot
    ) {
        super(Component.translatable("screen.zenkai.choose_style.title"));
        this.raceScreen    = raceScreen;
        this.statsSnapshot  = statsSnapshot;
        this.visualSnapshot = visualSnapshot;
    }

    @Override
    protected void init() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        this.leftPos = (this.width  - BG_W) / 2;
        this.topPos  = (this.height - BG_H) / 2;

        var visual = mc.player.getData(DataAttachments.PLAYER_VISUAL.get());
        this.rgbValue = visual.getAuraColorRgb() & 0xFFFFFF;

        var stats = mc.player.getData(DataAttachments.PLAYER_STATS.get());
        Style cur = stats.getStyle();
        for (int i = 0; i < styles.length; i++) {
            if (styles[i] == cur) { styleIndex = i; break; }
        }

        int lp = leftPos;
        int tp = topPos;

        addRenderableWidget(new ArrowIconButton(
                lp + PAD, tp + ROW_STYLE_Y,
                ArrowIconButton.Dir.LEFT,
                () -> styleIndex = (styleIndex - 1 + styles.length) % styles.length
        ));
        addRenderableWidget(new ArrowIconButton(
                lp + BG_W - PAD - 14, tp + ROW_STYLE_Y,
                ArrowIconButton.Dir.RIGHT,
                () -> styleIndex = (styleIndex + 1) % styles.length
        ));

        // HEX input alineado a la izquierda de la zona de color
        hexBox = new EditBox(this.font, lp + PAD, tp + HEX_BOX_Y, 80, 16, Component.empty());
        hexBox.setMaxLength(7);
        hexBox.setValue(String.format("#%06X", rgbValue));
        hexBox.setResponder(s -> {
            Integer parsed = parseHex(s);
            if (parsed != null) { rgbValue = parsed; applyColorPreview(); }
        });
        addRenderableWidget(hexBox);

        addRenderableWidget(Button.builder(
                Component.translatable("screen.zenkai.back"),
                b -> {
                    goingBack = true;
                    if (raceScreen != null) mc.setScreen(raceScreen);
                    else mc.setScreen(null);
                }
        ).bounds(lp + PAD, tp + BG_H - 26, 50, 20).build());

        addRenderableWidget(Button.builder(
                Component.translatable("screen.zenkai.confirm"),
                b -> onConfirm()
        ).bounds(lp + BG_W - PAD - 52, tp + BG_H - 26, 52, 20).build());
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        Style s = styles[styleIndex];
        String styleKey = "screen.zenkai.style." + s.name().toLowerCase();
        int lp = leftPos;
        int tp = topPos;

        // 1) Overlay oscuro
        super.renderBackground(g, mouseX, mouseY, partialTick);

        // 2) Textura del panel
        g.blit(BG, lp, tp, 0, 0, BG_W, BG_H);

        // 3) Textos y formas

        // Fila estilo
        g.drawString(mc.font,
                Component.translatable("screen.zenkai.label.style"),
                lp + PAD, tp + ROW_STYLE_Y - 1, COLOR_LABEL, false);
        g.drawCenteredString(mc.font,
                Component.translatable(styleKey),
                lp + BG_W / 2, tp + ROW_STYLE_Y, COLOR_VALUE);

        g.fill(lp + PAD, tp + DIV1_Y, lp + BG_W - PAD, tp + DIV1_Y + 1, 0x44FFFFFF);

        // Descripción
        String[] lines = wrapText(
                Component.translatable(styleKey + ".desc").getString(),
                mc.font, BG_W - PAD * 2
        );
        for (int i = 0; i < lines.length; i++) {
            g.drawString(mc.font, Component.literal(lines[i]),
                    lp + PAD, tp + DESC_Y + i * 10, COLOR_DESC, false);
        }

        g.fill(lp + PAD, tp + DIV2_Y, lp + BG_W - PAD, tp + DIV2_Y + 1, 0x44FFFFFF);

        // Labels de color
        g.drawString(mc.font,
                Component.translatable("screen.zenkai.label.ki_color"),
                lp + PAD, tp + COLOR_LBL_Y, COLOR_LABEL, false);
        g.drawString(mc.font,
                Component.translatable("screen.zenkai.label.hex"),
                lp + PAD, tp + HEX_LBL_Y, 0xFFFFFF, false);

        // Cuadro de color (derecha, alineado con el HEX input)
        int boxX = lp + BG_W - PAD - COLOR_BOX_W;
        int boxY = tp + HEX_BOX_Y - 2;
        g.fill(boxX - 1, boxY - 1, boxX + COLOR_BOX_W + 1, boxY + COLOR_BOX_H + 1, 0xFFFFFFFF);
        g.fill(boxX, boxY, boxX + COLOR_BOX_W, boxY + COLOR_BOX_H, 0xFF000000 | currentRgb());
        g.drawString(mc.font,
                Component.literal(String.format("#%06X", currentRgb())),
                boxX, boxY + COLOR_BOX_H + 4, 0x666666, false);

        // 4) Preview del jugador — zona inferior izquierda
        int previewX1 = lp + PAD;
        int previewY1 = tp + HEX_BOX_Y + 20;
        int previewX2 = lp + PAD + 70;
        int previewY2 = tp + BG_H - 32;
        InventoryScreen.renderEntityInInventoryFollowsMouse(
                g,
                previewX1, previewY1, previewX2, previewY2,
                PREVIEW_SIZE,
                0.0625f,
                (float) mouseX, (float) mouseY,
                mc.player
        );

        // 5) Widgets encima (siempre al final)
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public void renderBackground(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Vacío — el overlay se dibuja en render() para controlar el orden exacto
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

        visual.setAuraColorRgb(currentRgb());

        PacketDistributor.sendToServer(new ChooseRacePacket(stats.getRace()));
        PacketDistributor.sendToServer(UpdatePlayerVisualPacket.from(visual));
        PacketDistributor.sendToServer(new ChooseStylePacket(styles[styleIndex]));

        confirmed = true;
        if (raceScreen != null) raceScreen.markConfirmed();

        mc.setScreen(null);
    }

    private void applyColorPreview() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        mc.player.getData(DataAttachments.PLAYER_VISUAL.get()).setAuraColorRgb(currentRgb());
    }

    private int currentRgb() {
        if (hexBox != null) {
            Integer p = parseHex(hexBox.getValue());
            if (p != null) rgbValue = p;
        }
        return rgbValue & 0xFFFFFF;
    }

    private static Integer parseHex(String s) {
        if (s == null) return null;
        String t = s.trim().replaceFirst("^#", "");
        if (t.length() != 6) return null;
        try { return Integer.parseInt(t, 16) & 0xFFFFFF; }
        catch (Exception e) { return null; }
    }

    private String[] wrapText(String text, net.minecraft.client.gui.Font font, int maxWidth) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        String[] words = text.split(" ");
        StringBuilder line = new StringBuilder();
        for (String w : words) {
            String test = line.isEmpty() ? w : line + " " + w;
            if (font.width(test) > maxWidth && !line.isEmpty()) {
                lines.add(line.toString());
                line = new StringBuilder(w);
            } else {
                line = new StringBuilder(test);
            }
        }
        if (!line.isEmpty()) lines.add(line.toString());
        return lines.toArray(new String[0]);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}