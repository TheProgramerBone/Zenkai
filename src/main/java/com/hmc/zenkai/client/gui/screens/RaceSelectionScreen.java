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

    // ── Dimensiones exactas de la textura ────────────────────────────────────
    private static final int BG_W = 256;
    private static final int BG_H = 256;

    // Área utilizable del panel beige
    private static final int IN_X1 = 10;
    private static final int IN_Y1 = 10;
    private static final int IN_X2 = 245;
    private static final int IN_Y2 = 240;

    // Barra de botones
    private static final int BTN_BAR_Y = 260;
    private static final int BTN_W     = 60;

    // ── Layout interno ────────────────────────────────────────────────────────
    private static final int PAD     = 8;
    private static final int ARROW_W = 12;
    private static final int TITLE_H = 11; // separación título → fila de valor

    // Bloque 1: Race          Bloque 2: Skin Mode
    private static final int B1_TITLE_Y = IN_Y1 + 8;
    private static final int B1_VALUE_Y = B1_TITLE_Y + TITLE_H;
    private static final int DIV1_Y     = B1_VALUE_Y + 14;
    private static final int B2_TITLE_Y = DIV1_Y + 6;
    private static final int B2_VALUE_Y = B2_TITLE_Y + TITLE_H;
    private static final int DIV2_Y     = B2_VALUE_Y + 14;
    private static final int PREVIEW_SIZE = 50;

    // ── Colores ───────────────────────────────────────────────────────────────
    // Todo el texto usa el color secundario (blanco con sombra).
    private static final int COLOR_TEXT = 0xFFFFFF;

    // ── Estado ───────────────────────────────────────────────────────────────
    private int panelLeft, panelTop;
    private CompoundTag statsSnapshot, visualSnapshot;
    private boolean confirmed = false;
    private boolean goingNext = false;

    private final Race[] races = Race.values();
    private int raceIndex = 0;
    private boolean useCustomSkin = true;

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

        if (statsSnapshot == null) statsSnapshot  = stats.save();
        if (visualSnapshot == null) visualSnapshot = visual.save();

        Race cur = stats.getRace();
        for (int i = 0; i < races.length; i++) {
            if (races[i] == cur) { raceIndex = i; break; }
        }
        useCustomSkin = visual.shouldRenderRaceSkin();

        int pl = panelLeft;
        int pt = panelTop;

        // Fila raza (flechas a los lados de la fila de valor)
        int raceArrowY = pt + B1_VALUE_Y - 2;
        raceLeft  = new ArrowIconButton(pl + IN_X1 + PAD, raceArrowY,
                ArrowIconButton.Dir.LEFT,  () -> { raceIndex = (raceIndex - 1 + races.length) % races.length; applyPreview(); });
        raceRight = new ArrowIconButton(pl + IN_X2 - PAD - ARROW_W, raceArrowY,
                ArrowIconButton.Dir.RIGHT, () -> { raceIndex = (raceIndex + 1) % races.length; applyPreview(); });
        addRenderableWidget(raceLeft);
        addRenderableWidget(raceRight);

        // Fila skin mode
        int skinArrowY = pt + B2_VALUE_Y - 2;
        skinLeft  = new ArrowIconButton(pl + IN_X1 + PAD, skinArrowY,
                ArrowIconButton.Dir.LEFT,  () -> { useCustomSkin = !useCustomSkin; applyPreview(); });
        skinRight = new ArrowIconButton(pl + IN_X2 - PAD - ARROW_W, skinArrowY,
                ArrowIconButton.Dir.RIGHT, () -> { useCustomSkin = !useCustomSkin; applyPreview(); });
        addRenderableWidget(skinLeft);
        addRenderableWidget(skinRight);

        // Botones en la barra inferior
        addRenderableWidget(Button.builder(
                Component.translatable("screen.zenkai.cancel"),
                b -> { confirmed = false; goingNext = false; restoreSnapshots(); mc.setScreen(null); }
        ).bounds(pl + IN_X1, pt + BTN_BAR_Y, BTN_W, 20).build());

        addRenderableWidget(Button.builder(
                Component.translatable("screen.zenkai.next"),
                b -> { goingNext = true; mc.setScreen(new AppearanceScreen(this, statsSnapshot, visualSnapshot)); }
        ).bounds(pl + IN_X2 - BTN_W, pt + BTN_BAR_Y, BTN_W, 20).build());

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
        int cx = pl + BG_W / 2;

        super.renderBackground(g, mouseX, mouseY, partialTick);
        g.blit(BG, pl, pt, 0, 0, BG_W, BG_H);

        // Bloque raza — título arriba, valor (entre flechas) debajo
        g.drawCenteredString(mc.font, Component.translatable("screen.zenkai.label.race"),
                cx, pt + B1_TITLE_Y, COLOR_TEXT);
        g.drawCenteredString(mc.font,
                Component.translatable("screen.zenkai.race." + r.name().toLowerCase()),
                cx, pt + B1_VALUE_Y, COLOR_TEXT);

        g.fill(pl + IN_X1 + PAD, pt + DIV1_Y, pl + IN_X2 - PAD, pt + DIV1_Y + 1, 0x44FFFFFF);

        if (humanSaiyan) {
            g.drawCenteredString(mc.font, Component.translatable("screen.zenkai.label.skin"),
                    cx, pt + B2_TITLE_Y, COLOR_TEXT);
            g.drawCenteredString(mc.font,
                    Component.translatable(useCustomSkin
                            ? "screen.zenkai.skin.custom"
                            : "screen.zenkai.skin.vanilla"),
                    cx, pt + B2_VALUE_Y, COLOR_TEXT);
        }

        g.fill(pl + IN_X1 + PAD, pt + DIV2_Y, pl + IN_X2 - PAD, pt + DIV2_Y + 1, 0x44FFFFFF);

        // Preview jugador — centrado
        InventoryScreen.renderEntityInInventoryFollowsMouse(
                g,
                cx - 50, pt + DIV2_Y + 6,
                cx + 50, pt + IN_Y2 - 52,
                PREVIEW_SIZE, 0.0625f,
                (float) mouseX, (float) mouseY, mc.player);

        // Descripción raza (nombre + texto)
        int descX = pl + IN_X1 + PAD + 2;
        int descY = pt + IN_Y2 - 48;
        g.drawString(mc.font,
                Component.translatable("screen.zenkai.race." + r.name().toLowerCase()),
                descX, descY, COLOR_TEXT, true);

        String[] lines = wrapText(
                Component.translatable("screen.zenkai.race." + r.name().toLowerCase() + ".desc").getString(),
                mc.font, IN_X2 - IN_X1 - PAD * 2);
        for (int i = 0; i < lines.length; i++) {
            g.drawString(mc.font, Component.literal(lines[i]),
                    descX, descY + 12 + i * 10, COLOR_TEXT, true);
        }

        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override public void renderBackground(@NotNull GuiGraphics g, int mx, int my, float pt) {}
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
        setVisible(skinLeft, humanSaiyan);
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
        if (w == null) return; w.visible = v; w.active = v;
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