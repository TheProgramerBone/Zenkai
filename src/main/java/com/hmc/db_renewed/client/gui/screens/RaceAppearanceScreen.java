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
    private static final int BG_H = 256;

    private int panelLeft;
    private int panelTop;

    private CompoundTag statsSnapshot;
    private CompoundTag visualSnapshot;

    private final Race[] races = new Race[]{ Race.HUMAN, Race.SAIYAN, Race.NAMEKIAN, Race.ARCOSIAN, Race.MAJIN };
    private int raceIndex = 0;

    private boolean useCustomSkin = true;

    // hair0 = calvo, hair1 = pelo 1 (ajusta a tus ids reales)
    private final String[] hairIds = new String[]{ "hair0", "hair1" };
    private int hairIndex = 0;

    // ====== Widgets (opción PRO: ocultar/mostrar sin reconstruir) ======
    private ArrowIconButton raceLeft, raceRight;
    private ArrowIconButton hairLeft, hairRight;
    private ArrowIconButton skinLeft, skinRight;

    public RaceAppearanceScreen() {
        super(Component.translatable("screen." + DragonBlockRenewed.MOD_ID + ".appearance.title"));
    }

    @Override
    protected void init() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        this.clearWidgets();

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

        // hair init
        String hs = visual.getHairStyleId();
        for (int i = 0; i < hairIds.length; i++) {
            if (hairIds[i].equalsIgnoreCase(hs)) { hairIndex = i; break; }
        }

        int left = panelLeft;
        int top  = panelTop;

        // ====== Flechas de raza ======
        int rx = left + BG_W - 62;
        int ry = top + 10;

        raceLeft = new ArrowIconButton(rx, ry, ArrowIconButton.Dir.LEFT, () -> {
            raceIndex = (raceIndex - 1 + races.length) % races.length;
            applyPreview();
        });
        raceRight = new ArrowIconButton(rx + 46, ry, ArrowIconButton.Dir.RIGHT, () -> {
            raceIndex = (raceIndex + 1) % races.length;
            applyPreview();
        });
        addRenderableWidget(raceLeft);
        addRenderableWidget(raceRight);

        // ====== Hair (solo Human/Saiyan) ======
        int hx = left + BG_W - 62;
        int hy = top + 32;

        hairLeft = new ArrowIconButton(hx, hy, ArrowIconButton.Dir.LEFT, () -> {
            hairIndex = (hairIndex - 1 + hairIds.length) % hairIds.length;
            applyPreview();
        });
        hairRight = new ArrowIconButton(hx + 46, hy, ArrowIconButton.Dir.RIGHT, () -> {
            hairIndex = (hairIndex + 1) % hairIds.length;
            applyPreview();
        });
        addRenderableWidget(hairLeft);
        addRenderableWidget(hairRight);

        // ====== Toggle Custom/Vanilla (solo Human/Saiyan) ======
        int cx = left + BG_W - 34;
        int cy = top + 52;

        skinLeft = new ArrowIconButton(cx, cy, ArrowIconButton.Dir.LEFT, () -> {
            useCustomSkin = !useCustomSkin;
            applyPreview();
        });
        skinRight = new ArrowIconButton(cx + 14, cy, ArrowIconButton.Dir.RIGHT, () -> {
            useCustomSkin = !useCustomSkin;
            applyPreview();
        });
        addRenderableWidget(skinLeft);
        addRenderableWidget(skinRight);

        // ====== Botones ======
        addRenderableWidget(Button.builder(Component.literal("X"), b -> {
            restoreSnapshots();
            mc.setScreen(null);
        }).bounds(left + 6, top + BG_H - 24, 20, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Next"), b -> confirmAndNext())
                .bounds(left + BG_W - 48, top + BG_H - 24, 42, 20)
                .build());

        applyPreview();
    }

    private static void setWidgetVisible(ArrowIconButton w, boolean v) {
        if (w == null) return;
        w.visible = v;
        w.active = v;
    }

    private void refreshWidgetVisibility() {
        Race r = races[raceIndex];
        boolean humanSaiyan = (r == Race.HUMAN || r == Race.SAIYAN);

        // ocultar custom/vanilla en Namekian/Arcosian/Majin
        setWidgetVisible(skinLeft, humanSaiyan);
        setWidgetVisible(skinRight, humanSaiyan);

        // ocultar hair también si no es Human/Saiyan
        setWidgetVisible(hairLeft, humanSaiyan);
        setWidgetVisible(hairRight, humanSaiyan);

        // si cambia a raza no-humana, forzamos custom (así no “recuerda” vanilla raro)
        if (!humanSaiyan) {
            useCustomSkin = true;
        }
    }

    private void applyPreview() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        var stats  = mc.player.getData(DataAttachments.PLAYER_STATS.get());
        var visual = mc.player.getData(DataAttachments.PLAYER_VISUAL.get());

        Race r = races[raceIndex];

        // ====== PRO TIP: forzar raceChosen LOCAL para que tus layers no se apaguen en preview ======
        // Esto NO se guarda (snapshot revierte al salir / servidor manda lo real al confirmar)
        stats.setRaceChosen(true);

        // Preview local: cambiar race (esto alimenta RaceSkinSlots y cualquier resolver)
        stats.setRace(r);

        // Visibilidad de flechas
        refreshWidgetVisibility();

        boolean humanSaiyan = (r == Race.HUMAN || r == Race.SAIYAN);

        if (humanSaiyan) {
            // Hair id (incluye hair0=calvo)
            visual.setHairStyleId(hairIds[hairIndex]);

            // Custom/Vanilla coherente
            if (useCustomSkin) {
                visual.setRenderRaceSkin(true);
                visual.setHideVanillaBody(true);
            } else {
                visual.setRenderRaceSkin(false);
                visual.setHideVanillaBody(false);
            }
        } else {
            // razas no-humanas: siempre gecko
            visual.setRenderRaceSkin(true);
            visual.setHideVanillaBody(true);
        }
    }

    private void restoreSnapshots() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        var stats  = mc.player.getData(DataAttachments.PLAYER_STATS.get());
        var visual = mc.player.getData(DataAttachments.PLAYER_VISUAL.get());

        if (statsSnapshot != null)  stats.load(statsSnapshot);
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
    public void renderBackground(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.blit(BG, panelLeft, panelTop, 0, 0, BG_W, BG_H);
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            super.render(g, mouseX, mouseY, partialTick);
            return;
        }

        // 1) BG (como StatsScreen)
        this.renderBackground(g, mouseX, mouseY, partialTick);

        // 2) Player preview
        int px = panelLeft + 70;
        int py = panelTop + 190;
        int scale = 70;

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

        // 3) Labels
        Race r = races[raceIndex];

        g.drawString(mc.font, Component.literal(r.name()), panelLeft + BG_W - 105, panelTop + 14, 0xFFFFFFFF);

        boolean humanSaiyan = (r == Race.HUMAN || r == Race.SAIYAN);

        if (humanSaiyan) {
            g.drawString(mc.font,
                    Component.literal("Hair: " + hairIds[hairIndex]),
                    panelLeft + BG_W - 130, panelTop + 36, 0xFFFFFFFF
            );

            g.drawString(mc.font,
                    Component.literal(useCustomSkin ? "Custom Skin" : "Vanilla Skin"),
                    panelLeft + BG_W - 130, panelTop + 56, 0xFFFFFFFF
            );
        }

        // 4) Widgets encima
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
