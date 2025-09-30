package com.hmc.db_renewed.gui;

import com.hmc.db_renewed.network.wishes.OpenStackWishPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;


public class ShenlongWishScreen extends Screen {

    public ShenlongWishScreen() {
        super(Component.translatable("screen.db_renewed.shenlong_wish"));
    }

    private Button stackWishButton;

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        this.stackWishButton = addRenderableWidget(Button.builder(
                Component.translatable("screen.db_renewed.option.stack"),
                btn -> {
                    if (Minecraft.getInstance().getConnection() != null) {
                        Minecraft.getInstance().getConnection().send(new OpenStackWishPayload());
                    }
                }
        ).bounds(centerX - 50, centerY - 10, 100, 20)
                .build());
    }

    @Override
    public void tick() {
        super.tick();
        var mc = Minecraft.getInstance();
        boolean full = mc.player != null && mc.player.getInventory().getFreeSlot() == -1;

        if (this.stackWishButton != null) {
            this.stackWishButton.active = !full;

            if (full) {
                this.stackWishButton.setTooltip(Tooltip.create(
                        Component.translatable("screen.db_renewed.need_inventory_space")));
            } else {
                this.stackWishButton.setTooltip(null);
            }
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xFFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
