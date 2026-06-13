package com.hmc.zenkai.client.gui.screens;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.gui.buttons.ArrowIconButton;
import com.hmc.zenkai.core.network.feature.Race;
import com.hmc.zenkai.core.network.feature.stats.ChooseRacePacket;
import com.hmc.zenkai.core.network.feature.stats.DataAttachments;
import com.hmc.zenkai.core.network.feature.race.UpdatePlayerVisualPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

public class RaceAppearanceScreen extends Screen {

    // ── Assets ───────────────────────────────────────────────────────────────
    private static final ResourceLocation BG =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/common_screen.png");

    // Panel más ancho para que el preview quede a la izquierda
    // y las opciones a la derecha sin solaparse
    private static final int BG_W = 320;
    private static final int BG_H = 240;

    // Columna izquierda: preview del jugador
    private static final int PREVIEW_SECTION_W = 120;
    // Columna derecha: opciones
    private static final int OPTIONS_X_OFFSET  = 130; // desde panelLeft

    // ── Estado ───────────────────────────────────────────────────────────────
    private int panelLeft, panelTop;
    private CompoundTag statsSnapshot, visualSnapshot;

    private final Race[] races = Race.values();
    private int raceIndex = 0;

    private final String[] hairIds    = { "hair0", "hair1" };
    private final String[] hairLabels = { "Bald",  "Hair 1" };
    private int hairIndex = 0;

    private boolean useCustomSkin = true;

    // ── Widgets ───────────────────────────────────────────────────────────────
    private ArrowIconButton raceLeft, raceRight;
    private ArrowIconButton hairLeft, hairRight;
    private ArrowIconButton skinLeft, skinRight;

    public RaceAppearanceScreen() {
        super(Component.translatable("screen.db_renewed.appearance.title"));
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

        // Snapshots para cancelar
        statsSnapshot  = stats.save();
        visualSnapshot = visual.save();

        // Inicializar índices desde estado actual
        Race current = stats.getRace();
        for (int i = 0; i < races.length; i++) {
            if (races[i] == current) { raceIndex = i; break; }
        }
        useCustomSkin = visual.shouldRenderRaceSkin();
        String hs = visual.getHairStyleId();
        for (int i = 0; i < hairIds.length; i++) {
            if (hairIds[i].equalsIgnoreCase(hs)) { hairIndex = i; break; }
        }

        // ── Zona de opciones (columna derecha) ───────────────────────────────
        int ox = panelLeft + OPTIONS_X_OFFSET;

        // Fila 1: Raza
        int row1Y = panelTop + 20;
        raceLeft  = new ArrowIconButton(ox,      row1Y, ArrowIconButton.Dir.LEFT,  () -> { raceIndex = (raceIndex - 1 + races.length) % races.length; applyPreview(); });
        raceRight = new ArrowIconButton(ox + 110, row1Y, ArrowIconButton.Dir.RIGHT, () -> { raceIndex = (raceIndex + 1) % races.length; applyPreview(); });
        addRenderableWidget(raceLeft);
        addRenderableWidget(raceRight);

        // Fila 2: Pelo
        int row2Y = panelTop + 60;
        hairLeft  = new ArrowIconButton(ox,      row2Y, ArrowIconButton.Dir.LEFT,  () -> { hairIndex = (hairIndex - 1 + hairIds.length) % hairIds.length; applyPreview(); });
        hairRight = new ArrowIconButton(ox + 110, row2Y, ArrowIconButton.Dir.RIGHT, () -> { hairIndex = (hairIndex + 1) % hairIds.length; applyPreview(); });
        addRenderableWidget(hairLeft);
        addRenderableWidget(hairRight);

        // Fila 3: Skin mode
        int row3Y = panelTop + 100;
        skinLeft  = new ArrowIconButton(ox,      row3Y, ArrowIconButton.Dir.LEFT,  () -> { useCustomSkin = !useCustomSkin; applyPreview(); });
        skinRight = new ArrowIconButton(ox + 110, row3Y, ArrowIconButton.Dir.RIGHT, () -> { useCustomSkin = !useCustomSkin; applyPreview(); });
        addRenderableWidget(skinLeft);
        addRenderableWidget(skinRight);

        // ── Botones de acción ────────────────────────────────────────────────
        addRenderableWidget(Button.builder(
                Component.translatable("screen.db_renewed.cancel"),
                b -> { restoreSnapshots(); mc.setScreen(null); }
        ).bounds(panelLeft + 8, panelTop + BG_H - 26, 50, 20).build());

        addRenderableWidget(Button.builder(
                Component.translatable("screen.db_renewed.next"),
                b -> confirmAndNext()
        ).bounds(panelLeft + BG_W - 60, panelTop + BG_H - 26, 52, 20).build());

        applyPreview();
    }

    // ── Render ────────────────────────────────────────────────────────────────
    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) { super.render(g, mouseX, mouseY, partialTick); return; }

        // Fondo
        renderBackground(g, mouseX, mouseY, partialTick);

        // ── Preview del jugador (columna izquierda, centrado verticalmente) ──
        int previewX = panelLeft + PREVIEW_SECTION_W / 2;
        int previewY = panelTop + BG_H - 30;
        int scale = 55;
        InventoryScreen.renderEntityInInventoryFollowsMouse(
                g, previewX, previewY, scale,
                previewX - mouseX, previewY - 50 - mouseY,
                0f, 0f, 0f, mc.player
        );

        // ── Divisor vertical ─────────────────────────────────────────────────
        g.fill(panelLeft + PREVIEW_SECTION_W + 5, panelTop + 10,
                panelLeft + PREVIEW_SECTION_W + 6, panelTop + BG_H - 10,
                0x88FFFFFF);

        // ── Labels de opciones ───────────────────────────────────────────────
        int ox = panelLeft + OPTIONS_X_OFFSET;
        Race r = races[raceIndex];
        boolean humanSaiyan = (r == Race.HUMAN || r == Race.SAIYAN);

        // Fila 1: Raza
        g.drawString(mc.font, Component.translatable("screen.db_renewed.label.race"),
                ox, panelTop + 10, 0xAAAAAA);
        g.drawCenteredString(mc.font,
                Component.translatable("screen.db_renewed.race." + r.name().toLowerCase()),
                ox + 65, panelTop + 23, 0xFFFFFF);

        // Fila 2: Pelo (solo Human/Saiyan)
        if (humanSaiyan) {
            g.drawString(mc.font, Component.translatable("screen.db_renewed.label.hair"),
                    ox, panelTop + 50, 0xAAAAAA);
            g.drawCenteredString(mc.font,
                    Component.literal(hairLabels[hairIndex]),
                    ox + 65, panelTop + 63, 0xFFFFFF);

            // Fila 3: Skin mode
            g.drawString(mc.font, Component.translatable("screen.db_renewed.label.skin"),
                    ox, panelTop + 90, 0xAAAAAA);
            g.drawCenteredString(mc.font,
                    Component.translatable(useCustomSkin
                            ? "screen.db_renewed.skin.custom"
                            : "screen.db_renewed.skin.vanilla"),
                    ox + 65, panelTop + 103, 0xFFFFFF);
        }

        // ── Descripción de la raza (abajo de las opciones) ───────────────────
        g.drawString(mc.font,
                Component.translatable("screen.db_renewed.race." + r.name().toLowerCase() + ".desc"),
                ox, panelTop + 145, 0x888888);

        // Widgets encima
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public void renderBackground(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        g.blit(BG, panelLeft, panelTop, 0, 0, BG_W, BG_H);
    }

    // ── Lógica interna ────────────────────────────────────────────────────────
    private void applyPreview() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        var stats  = mc.player.getData(DataAttachments.PLAYER_STATS.get());
        var visual = mc.player.getData(DataAttachments.PLAYER_VISUAL.get());
        Race r = races[raceIndex];
        boolean humanSaiyan = (r == Race.HUMAN || r == Race.SAIYAN);

        stats.setRaceChosen(true);
        stats.setRace(r);

        refreshWidgetVisibility(humanSaiyan);

        if (humanSaiyan) {
            visual.setHairStyleId(hairIds[hairIndex]);
            visual.setRenderRaceSkin(useCustomSkin);
            visual.setHideVanillaBody(useCustomSkin);
        } else {
            visual.setRenderRaceSkin(true);
            visual.setHideVanillaBody(true);
        }
    }

    private void refreshWidgetVisibility(boolean humanSaiyan) {
        setVisible(hairLeft,  humanSaiyan);
        setVisible(hairRight, humanSaiyan);
        setVisible(skinLeft,  humanSaiyan);
        setVisible(skinRight, humanSaiyan);
        if (!humanSaiyan) useCustomSkin = true;
    }

    private static void setVisible(ArrowIconButton w, boolean v) {
        if (w == null) return;
        w.visible = v;
        w.active  = v;
    }

    private void restoreSnapshots() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        var stats  = mc.player.getData(DataAttachments.PLAYER_STATS.get());
        var visual = mc.player.getData(DataAttachments.PLAYER_VISUAL.get());
        if (statsSnapshot  != null) stats.load(statsSnapshot);
        if (visualSnapshot != null) visual.load(visualSnapshot);
    }

    private void confirmAndNext() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        var stats  = mc.player.getData(DataAttachments.PLAYER_STATS.get());
        var visual = mc.player.getData(DataAttachments.PLAYER_VISUAL.get());
        PacketDistributor.sendToServer(new ChooseRacePacket(stats.getRace()));
        PacketDistributor.sendToServer(UpdatePlayerVisualPacket.from(visual));
        mc.setScreen(new StyleSelectionScreen(this));
    }

    @Override
    public void removed() {
        restoreSnapshots();
        super.removed();
    }

    @Override
    public boolean isPauseScreen() { return false; }
}