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

    private Button stackButton;

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        this.stackButton = addRenderableWidget(Button.builder(
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
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        var mc = Minecraft.getInstance();
        boolean full = mc.player != null && mc.player.getInventory().getFreeSlot() == -1;

        // Si el botón está bajo el mouse, ponemos/quitar el tooltip dinámico
        if (this.stackButton.isHovered()) {
            if (full) {
                this.stackButton.setTooltip(Tooltip.create(
                        Component.translatable("screen.db_renewed.need_inventory_space")));
            } else {
                this.stackButton.setTooltip(null);
            }
        }
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xFFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
