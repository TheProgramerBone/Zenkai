package com.hmc.db_renewed.client.gui.screens;

import com.hmc.db_renewed.DragonBlockRenewed;
import com.hmc.db_renewed.client.gui.buttons.ArrowIconButton;
import com.hmc.db_renewed.core.network.feature.Race;
import com.hmc.db_renewed.core.network.feature.stats.ChooseRacePacket;
import com.hmc.db_renewed.core.network.feature.stats.DataAttachments;
import com.hmc.db_renewed.core.network.feature.stats.UpdatePlayerVisualPacket;
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

    private static final ResourceLocation BG =
            ResourceLocation.fromNamespaceAndPath(DragonBlockRenewed.MOD_ID, "textures/gui/common_screen.png");

    private static final int BG_W = 256;
    private static final int BG_H = 128;

    // Igual que StatsScreen: posición calculada en init()
    private int panelLeft;
    private int panelTop;

    private CompoundTag statsSnapshot;
    private CompoundTag visualSnapshot;

    private final Race[] races = new Race[]{ Race.HUMAN, Race.SAIYAN, Race.NAMEKIAN, Race.ARCOSIAN, Race.MAJIN };
    private int raceIndex = 0;

    // “Custom Skin” vs Vanilla
    private boolean useCustomSkin = true;

    // Para Saiyan: hair0/hair1 (ajusta a tus ids reales)
    private final String[] hairIds = new String[]{ "hair0", "hair1" };
    private int hairIndex = 0;

    public RaceAppearanceScreen() {
        super(Component.translatable("screen." + DragonBlockRenewed.MOD_ID + ".appearance.title"));
    }

    @Override
    protected void init() {
        var mc = Minecraft.getInstance();
        if (mc.player == null) return;

        this.clearWidgets();

        // calcular panel como StatsScreen
        this.panelLeft = (this.width - BG_W) / 2;
        this.panelTop  = (this.height - BG_H) / 2;

        var stats  = mc.player.getData(DataAttachments.PLAYER_STATS.get());
        var visual = mc.player.getData(DataAttachments.PLAYER_VISUAL.get());

        // snapshots para cancelar
        statsSnapshot  = stats.save();
        visualSnapshot = visual.save();

        // init raceIndex desde stats
        Race current = stats.getRace();
        for (int i = 0; i < races.length; i++) {
            if (races[i] == current) { raceIndex = i; break; }
        }

        // init toggles desde visual
        useCustomSkin = visual.shouldRenderRaceSkin();

        // hair
        String hs = visual.getHairStyleId();
        for (int i = 0; i < hairIds.length; i++) {
            if (hairIds[i].equalsIgnoreCase(hs)) { hairIndex = i; break; }
        }

        int left = panelLeft;
        int top  = panelTop;

        // Flechas de raza (arriba derecha como la imagen)
        int rx = left + BG_W - 62;
        int ry = top + 10;

        addRenderableWidget(new ArrowIconButton(rx, ry, ArrowIconButton.Dir.LEFT, () -> {
            raceIndex = (raceIndex - 1 + races.length) % races.length;
            applyPreview();
        }));

        addRenderableWidget(new ArrowIconButton(rx + 46, ry, ArrowIconButton.Dir.RIGHT, () -> {
            raceIndex = (raceIndex + 1) % races.length;
            applyPreview();
        }));

        // Hair (solo si Saiyan) (posición ejemplo)
        int hx = left + BG_W - 62;
        int hy = top + 32;

        addRenderableWidget(new ArrowIconButton(hx, hy, ArrowIconButton.Dir.LEFT, () -> {
            hairIndex = (hairIndex - 1 + hairIds.length) % hairIds.length;
            applyPreview();
        }));

        addRenderableWidget(new ArrowIconButton(hx + 46, hy, ArrowIconButton.Dir.RIGHT, () -> {
            hairIndex = (hairIndex + 1) % hairIds.length;
            applyPreview();
        }));

        // Toggle Custom Skin (flechas al lado)
        int cx = left + BG_W - 34;
        int cy = top + 52;

        addRenderableWidget(new ArrowIconButton(cx, cy, ArrowIconButton.Dir.LEFT, () -> {
            useCustomSkin = !useCustomSkin;
            applyPreview();
        }));
        addRenderableWidget(new ArrowIconButton(cx + 14, cy, ArrowIconButton.Dir.RIGHT, () -> {
            useCustomSkin = !useCustomSkin;
            applyPreview();
        }));

        // Botón X (abajo izquierda)
        addRenderableWidget(Button.builder(Component.literal("X"), b -> {
            restoreSnapshots();
            mc.setScreen(null);
        }).bounds(left + 6, top + BG_H - 24, 20, 20).build());

        // Botón Next (abajo derecha)
        addRenderableWidget(Button.builder(Component.literal("Next"), b -> confirmAndNext())
                .bounds(left + BG_W - 48, top + BG_H - 24, 42, 20)
                .build());

        applyPreview();
    }

    private void applyPreview() {
        var mc = Minecraft.getInstance();
        if (mc.player == null) return;

        var stats  = mc.player.getData(DataAttachments.PLAYER_STATS.get());
        var visual = mc.player.getData(DataAttachments.PLAYER_VISUAL.get());

        Race r = races[raceIndex];

        // Preview local
        stats.setRace(r);

        // Custom skin vs vanilla
        // Custom skin vs vanilla
        if (r == Race.HUMAN || r == Race.SAIYAN) {
            visual.setHairStyleId(hairIds[hairIndex]);
            visual.setRenderRaceSkin(useCustomSkin);
            visual.setHideVanillaBody(useCustomSkin);
        } else {
            // razas no-humanas: siempre gecko
            visual.setRenderRaceSkin(true);
            visual.setHideVanillaBody(true);
        }

        if (!visual.shouldRenderRaceSkin()) {
            visual.setHideVanillaBody(false);
        }
    }

    private void restoreSnapshots() {
        var mc = Minecraft.getInstance();
        if (mc.player == null) return;

        var stats  = mc.player.getData(DataAttachments.PLAYER_STATS.get());
        var visual = mc.player.getData(DataAttachments.PLAYER_VISUAL.get());

        if (statsSnapshot != null)  stats.load(statsSnapshot);
        if (visualSnapshot != null) visual.load(visualSnapshot);
    }

    private void confirmAndNext() {
        var mc = Minecraft.getInstance();
        if (mc.player == null) return;

        var stats  = mc.player.getData(DataAttachments.PLAYER_STATS.get());
        var visual = mc.player.getData(DataAttachments.PLAYER_VISUAL.get());

        PacketDistributor.sendToServer(new ChooseRacePacket(stats.getRace()));
        PacketDistributor.sendToServer(UpdatePlayerVisualPacket.from(visual));

        mc.setScreen(new StyleSelectionScreen(this));
    }

    @Override
    public void removed() {
        // si cierra con ESC: revertir
        restoreSnapshots();
        super.removed();
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Fondo
        g.blit(BG, panelLeft, panelTop, 0, 0, BG_W, BG_H);

        // Preview del jugador (debajo de widgets)
        var mc = Minecraft.getInstance();
        if (mc.player != null) {
            int px = panelLeft + 60;
            int py = panelTop + 95;
            int scale = 40;

            // Estos dos floats suelen ser "cómo rota" con el mouse
            float relX = (float) (px - mouseX);
            float relY = (float) (py - mouseY);

            // Firma típica (si tu mapeo cambia, me dices y lo ajusto)
            InventoryScreen.renderEntityInInventoryFollowsMouse(
                    g,
                    px, py,
                    scale,
                    (int) relX, (int) relY,     // mouse deltas
                    0.0F, 0.0F, 0.0F, // rot extra (déjalo en 0 por ahora)
                    mc.player
            );
        }
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g, mouseX, mouseY, partialTick);

        int left = (width - BG_W) / 2;
        int top  = (height - BG_H) / 2;

        // BG del menú
        g.blit(BG, left, top, 0, 0, BG_W, BG_H);

        var mc = Minecraft.getInstance();
        if (mc.player != null) {
            Race r = races[raceIndex];

            g.drawString(mc.font, Component.literal(r.name()), left + BG_W - 105, top + 14, 0xFFFFFFFF);
            g.drawString(mc.font,
                    Component.literal(useCustomSkin ? "Custom Skin" : "Vanilla Skin"),
                    left + BG_W - 130, top + 56, 0xFFFFFFFF
            );

            // Humanos también tienen pelo (igual que Saiyan)
            if (r == Race.SAIYAN || r == Race.HUMAN) {
                g.drawString(mc.font, Component.literal("Hair: " + hairIds[hairIndex]),
                        left + BG_W - 130, top + 36, 0xFFFFFFFF);
            }

            // Preview del jugador
            int px = left + 60;
            int py = top + 95;
            int scale = 40;

            int relX = px - mouseX;
            int relY = py - mouseY;

            InventoryScreen.renderEntityInInventoryFollowsMouse(
                    g,
                    px, py,
                    scale,
                    relX, relY,
                    0.0F, 0.0F, 0.0F,
                    mc.player
            );
        }

        // Widgets al final (encima del BG y del preview)
        super.render(g, mouseX, mouseY, partialTick);
    }


    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
