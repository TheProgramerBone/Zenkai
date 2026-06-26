package com.hmc.zenkai.client.gui.screens;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.gui.buttons.TextOnlyButton;
import com.hmc.zenkai.client.gui.screens.wishes.EnchantVillagerWishScreen;
import com.hmc.zenkai.client.gui.screens.wishes.ImmortalWishScreen;
import com.hmc.zenkai.client.gui.screens.wishes.RevivePlayerWishScreen;
import com.hmc.zenkai.client.gui.screens.wishes.TrainingPointsWishScreen;
import com.hmc.zenkai.core.network.feature.wishes.OpenStackWishPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public class ShenlongWishScreen extends Screen {

    private static final ResourceLocation BG =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/common_screen.png");

    private static final int BG_W = 256;
    private static final int BG_H = 256;

    private static final int BTN_W = 170;
    private static final int BTN_H = 16;
    private static final int FIRST_BTN_DY = 46; // desde el top del panel
    private static final int BTN_STEP      = 22;

    // Colores de texto (consistentes con las otras pantallas, legibles sobre el beige).
    private static final int COLOR_TITLE = 0x4A3726;
    private static final int TXT_NORMAL  = 0x4A3726;
    private static final int TXT_HOVER   = 0x8A6A1E;
    private static final int TXT_INACTIVE= 0xA0A0A0;

    private int panelLeft, panelTop;
    private TextOnlyButton stackWishButton;

    public ShenlongWishScreen() {
        super(Component.translatable("screen.zenkai.shenlong_wish"));
    }

    @Override
    protected void init() {
        this.clearWidgets();
        this.panelLeft = (this.width  - BG_W) / 2;
        this.panelTop  = (this.height - BG_H) / 2;

        int cx = panelLeft + BG_W / 2;
        int bx = cx - BTN_W / 2;
        int y  = panelTop + FIRST_BTN_DY;

        // Stack de ítems (vía payload al servidor)
        this.stackWishButton = addRenderableWidget(new TextOnlyButton(
                bx, y, BTN_W, BTN_H,
                Component.translatable("screen.zenkai.option.stack"),
                () -> {
                    var conn = Minecraft.getInstance().getConnection();
                    if (conn != null) conn.send(new OpenStackWishPayload());
                }).textColors(TXT_NORMAL, TXT_HOVER, TXT_INACTIVE));
        y += BTN_STEP;

        addRenderableWidget(new TextOnlyButton(
                bx, y, BTN_W, BTN_H,
                Component.translatable("screen.zenkai.wish.revive_player"),
                () -> { if (minecraft != null) minecraft.setScreen(new RevivePlayerWishScreen(this)); })
                .textColors(TXT_NORMAL, TXT_HOVER, TXT_INACTIVE));
        y += BTN_STEP;

        addRenderableWidget(new TextOnlyButton(
                bx, y, BTN_W, BTN_H,
                Component.translatable("screen.zenkai.wish.enchant_villager"),
                () -> { if (minecraft != null) minecraft.setScreen(new EnchantVillagerWishScreen(this)); })
                .textColors(TXT_NORMAL, TXT_HOVER, TXT_INACTIVE));
        y += BTN_STEP;

        addRenderableWidget(new TextOnlyButton(
                bx, y, BTN_W, BTN_H,
                Component.translatable("screen.zenkai.wish.immortal"),
                () -> { if (minecraft != null) minecraft.setScreen(new ImmortalWishScreen(this)); })
                .textColors(TXT_NORMAL, TXT_HOVER, TXT_INACTIVE));
        y += BTN_STEP;

        addRenderableWidget(new TextOnlyButton(
                bx, y, BTN_W, BTN_H,
                Component.translatable("screen.zenkai.wish.training_points"),
                () -> { if (minecraft != null) minecraft.setScreen(new TrainingPointsWishScreen(this)); })
                .textColors(TXT_NORMAL, TXT_HOVER, TXT_INACTIVE));
    }

    @Override
    public void tick() {
        super.tick();
        var mc = Minecraft.getInstance();
        boolean full = mc.player != null && mc.player.getInventory().getFreeSlot() == -1;

        if (this.stackWishButton != null) {
            this.stackWishButton.active = !full;
            this.stackWishButton.setTooltip(full
                    ? Tooltip.create(Component.translatable("screen.zenkai.need_inventory_space"))
                    : null);
        }
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        g.blit(BG, panelLeft, panelTop, 0, 0, BG_W, BG_H);

        // Título centrado en el panel
        drawCenteredNoShadow(g, this.title, panelLeft + BG_W / 2, panelTop + 22, COLOR_TITLE);

        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override public void renderBackground(@NotNull GuiGraphics g, int mx, int my, float pt) {}

    private void drawCenteredNoShadow(GuiGraphics g, Component text, int cx, int y, int color) {
        g.drawString(this.font, text, cx - this.font.width(text) / 2, y, color, false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}