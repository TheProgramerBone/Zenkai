package com.hmc.db_renewed.client.gui;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

public class RaceSelectionScreen extends Screen {

    public RaceSelectionScreen() {
        super(Component.literal("Selecciona tu raza"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        // Aquí pondrás botones, imágenes, etc.
    }

    @Override
    public void render(net.minecraft.client.gui.@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(graphics, mouseX, mouseY, partialTicks);
        graphics.drawCenteredString(this.font, this.title.getString(), this.width / 2, this.height / 2, 0xFFFFFF);
        super.render(graphics, mouseX, mouseY, partialTicks);
    }
}