package com.hmc.db_renewed.gui;

import com.hmc.db_renewed.network.wishes.OpenStackWishPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;


public class ShenlongWishScreen extends Screen {

    public ShenlongWishScreen() {
        super(Component.translatable("screen.db_renewed.shenlong_wish"));
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        this.addRenderableWidget(Button.builder(
                Component.translatable("screen.db_renewed.option.stack"),
                btn -> {
                    if (Minecraft.getInstance().getConnection() != null) {
                        Minecraft.getInstance().getConnection().send(new OpenStackWishPayload());
                    }
                }
        ).bounds(centerX - 50, centerY - 10, 100, 20).build());

        // Aquí puedes añadir más botones para otros deseos que abran otras sub-pantallas
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xFFFFFF);
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
