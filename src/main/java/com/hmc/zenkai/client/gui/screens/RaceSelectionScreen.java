package com.hmc.zenkai.client.gui.screens;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.gui.buttons.ArrowIconButton;
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

public class RaceSelectionScreen extends Screen {

    private static final ResourceLocation BG =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/common_screen.png");
    private static final int BG_W = 256;
    private static final int BG_H = 256;

    private static final int PAD           = 10;
    private static final int ROW_RACE_Y    = 12;
    private static final int DIV1_Y        = 30;
    private static final int ROW_SKIN_Y    = 38;
    private static final int DIV2_Y        = 56;
    private static final int PREVIEW_SIZE  = 40;

    private static final int COLOR_LABEL     = 0xC8A96E;
    private static final int COLOR_VALUE     = 0xFFFFFF;
    private static final int COLOR_RACE_NAME = 0xFFDDAA;
    private static final int COLOR_DESC      = 0x999999;

    private int panelLeft, panelTop;
    private CompoundTag statsSnapshot, visualSnapshot;

    private boolean confirmed = false;
    private boolean goingNext = false;

    private final Race[] races     = Race.values();
    private int          raceIndex = 0;
    private boolean      useCustomSkin = true;

    private ArrowIconButton raceLeft, raceRight;
    private ArrowIconButton skinLeft, skinRight;

    public RaceSelectionScreen() {
        super(Component.translatable("screen.zenkai.appearance.title"));
    }

    @Override
    protected void init() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        this.clearWidgets();
        this.panelLeft = (this.width  - BG_W) / 2;
        this.panelTop  = (this.height - BG_H) / 2;

        goingNext = false;

        var stats  = mc.player.getData(DataAttachments.PLAYER_STATS.get());
        var visual = mc.player.getData(DataAttachments.PLAYER_VISUAL.get());

        if (statsSnapshot == null)  statsSnapshot  = stats.save();
        if (visualSnapshot == null) visualSnapshot = visual.save();

        Race cur = stats.getRace();
        for (int i = 0; i < races.length; i++) {
            if (races[i] == cur) { raceIndex = i; break; }
        }
        useCustomSkin = visual.shouldRenderRaceSkin();

        int pl = panelLeft;
        int pt = panelTop;

        // ── Selector de raza ──────────────────────────────────────────────────
        raceLeft  = new ArrowIconButton(pl + PAD, pt + ROW_RACE_Y,
                ArrowIconButton.Dir.LEFT,  () -> { raceIndex = (raceIndex - 1 + races.length) % races.length; applyPreview(); });
        raceRight = new ArrowIconButton(pl + BG_W - PAD - 14, pt + ROW_RACE_Y,
                ArrowIconButton.Dir.RIGHT, () -> { raceIndex = (raceIndex + 1) % races.length; applyPreview(); });
        addRenderableWidget(raceLeft);
        addRenderableWidget(raceRight);

        // ── Selector de skin mode (solo Human/Saiyan) ─────────────────────────
        skinLeft  = new ArrowIconButton(pl + PAD, pt + ROW_SKIN_Y,
                ArrowIconButton.Dir.LEFT,  () -> { useCustomSkin = !useCustomSkin; applyPreview(); });
        skinRight = new ArrowIconButton(pl + BG_W - PAD - 14, pt + ROW_SKIN_Y,
                ArrowIconButton.Dir.RIGHT, () -> { useCustomSkin = !useCustomSkin; applyPreview(); });
        addRenderableWidget(skinLeft);
        addRenderableWidget(skinRight);

        // ── Botones ───────────────────────────────────────────────────────────
        addRenderableWidget(Button.builder(
                Component.translatable("screen.zenkai.cancel"),
                b -> { confirmed = false; goingNext = false; restoreSnapshots(); mc.setScreen(null); }
        ).bounds(pl + PAD, pt + BG_H - 26, 50, 20).build());

        addRenderableWidget(Button.builder(
                Component.translatable("screen.zenkai.next"),
                b -> { goingNext = true; mc.setScreen(new AppearanceScreen(this, statsSnapshot, visualSnapshot)); }
        ).bounds(pl + BG_W - PAD - 52, pt + BG_H - 26, 52, 20).build());

        applyPreview();
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        int pl = panelLeft;
        int pt = panelTop;
        Race r = races[raceIndex];
        boolean humanSaiyan = (r == Race.HUMAN || r == Race.SAIYAN);

        super.renderBackground(g, mouseX, mouseY, partialTick);
        g.blit(BG, pl, pt, 0, 0, BG_W, BG_H);

        // ── Fila de raza ──────────────────────────────────────────────────────
        g.drawString(mc.font, Component.translatable("screen.zenkai.label.race"),
                pl + PAD + 16, pt + ROW_RACE_Y + 2, COLOR_LABEL, false);
        g.drawCenteredString(mc.font,
                Component.translatable("screen.zenkai.race." + r.name().toLowerCase()),
                pl + BG_W / 2, pt + ROW_RACE_Y + 2, COLOR_VALUE);

        g.fill(pl + PAD, pt + DIV1_Y, pl + BG_W - PAD, pt + DIV1_Y + 1, 0x44FFFFFF);

        // ── Fila de skin mode (solo Human/Saiyan) ─────────────────────────────
        if (humanSaiyan) {
            g.drawString(mc.font, Component.translatable("screen.zenkai.label.skin"),
                    pl + PAD + 16, pt + ROW_SKIN_Y + 2, COLOR_LABEL, false);
            g.drawCenteredString(mc.font,
                    Component.translatable(useCustomSkin
                            ? "screen.zenkai.skin.custom"
                            : "screen.zenkai.skin.vanilla"),
                    pl + BG_W / 2, pt + ROW_SKIN_Y + 2, COLOR_VALUE);
        }

        g.fill(pl + PAD, pt + DIV2_Y, pl + BG_W - PAD, pt + DIV2_Y + 1, 0x44FFFFFF);

        // ── Preview del jugador ───────────────────────────────────────────────
        int previewX1 = pl + PAD;
        int previewY1 = pt + DIV2_Y + 6;
        int previewX2 = pl + PAD + 75;
        int previewY2 = pt + BG_H - 34;
        InventoryScreen.renderEntityInInventoryFollowsMouse(
                g, previewX1, previewY1, previewX2, previewY2,
                PREVIEW_SIZE, 0.0625f,
                (float) mouseX, (float) mouseY, mc.player
        );

        // ── Descripción de la raza ────────────────────────────────────────────
        int descX = pl + PAD + 80;
        int descY = pt + DIV2_Y + 8;
        g.drawString(mc.font,
                Component.translatable("screen.zenkai.race." + r.name().toLowerCase()),
                descX, descY, COLOR_RACE_NAME, false);
        String[] lines = wrapText(
                Component.translatable("screen.zenkai.race." + r.name().toLowerCase() + ".desc").getString(),
                mc.font, BG_W - (descX - pl) - PAD
        );
        for (int i = 0; i < lines.length; i++) {
            g.drawString(mc.font, Component.literal(lines[i]),
                    descX, descY + 12 + i * 10, COLOR_DESC, false);
        }

        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public void renderBackground(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {}

    public void markConfirmed() { this.confirmed = true; }

    @Override
    public void removed() {
        if (!confirmed && !goingNext) restoreSnapshots();
        super.removed();
    }

    private void applyPreview() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        var stats  = mc.player.getData(DataAttachments.PLAYER_STATS.get());
        var visual = mc.player.getData(DataAttachments.PLAYER_VISUAL.get());
        Race r = races[raceIndex];
        boolean humanSaiyan = (r == Race.HUMAN || r == Race.SAIYAN);

        stats.setRaceChosen(true);
        stats.setRace(r);

        // Skin mode solo aplica a Human/Saiyan
        setVisible(skinLeft,  humanSaiyan);
        setVisible(skinRight, humanSaiyan);
        if (!humanSaiyan) useCustomSkin = true;

        if (humanSaiyan) {
            visual.setRenderRaceSkin(useCustomSkin);
            visual.setHideVanillaBody(useCustomSkin);
        } else {
            visual.setRenderRaceSkin(true);
            visual.setHideVanillaBody(true);
        }
    }

    void restoreSnapshots() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        var stats  = mc.player.getData(DataAttachments.PLAYER_STATS.get());
        var visual = mc.player.getData(DataAttachments.PLAYER_VISUAL.get());
        if (statsSnapshot  != null) stats.load(statsSnapshot);
        if (visualSnapshot != null) visual.load(visualSnapshot);
    }

    private static void setVisible(ArrowIconButton w, boolean v) {
        if (w == null) return;
        w.visible = v;
        w.active  = v;
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