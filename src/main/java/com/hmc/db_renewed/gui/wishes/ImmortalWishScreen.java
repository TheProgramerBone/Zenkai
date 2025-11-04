package com.hmc.db_renewed.gui.wishes;

import com.hmc.db_renewed.network.wishes.WishImmortalPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class ImmortalWishScreen extends Screen {
    private final Screen parent;
    public ImmortalWishScreen(Screen parent) {
        super(Component.translatable("screen.db_renewed.wish.immortal"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int cy = this.height / 2;

        this.addRenderableWidget(Button.builder(Component.translatable("screen.db_renewed.gui.confirm"), b -> {
            var conn = Minecraft.getInstance().getConnection();
            if (conn != null) conn.send(new WishImmortalPayload()); // <-- envia al server
            this.onClose();
        }).bounds(cx - 60, cy, 120, 20).build());

        this.addRenderableWidget(Button.builder(Component.translatable("screen.db_renewed.gui.back"), b -> this.onClose())
                .bounds(cx - 60, cy + 24, 120, 20).build());
    }

    @Override public void onClose() { this.minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        this.renderBackground(g,mouseX,mouseY,partial);
        super.render(g, mouseX, mouseY, partial);
        g.drawCenteredString(this.font, this.title, this.width/2, 20, 0xFFFFFF);
        g.drawCenteredString(this.font, Component.translatable("screen.db_renewed.wish.immortal.desc"),
                this.width/2, this.height/2 - 20, 0xAAAAAA);
    }
}
