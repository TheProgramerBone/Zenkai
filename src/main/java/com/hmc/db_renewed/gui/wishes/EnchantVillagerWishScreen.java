package com.hmc.db_renewed.gui.wishes;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

public class EnchantVillagerWishScreen extends Screen {
    private final Screen parent;
    public EnchantVillagerWishScreen(Screen parent) {
        super(Component.translatable("screen.db_renewed.wish.enchant_villager"));
        this.parent = parent;
    }
    @Override protected void init() {
        int cx = this.width/2, cy = this.height/2;
        this.addRenderableWidget(Button.builder(Component.translatable("screen.db_renewed.gui.confirm"), b -> this.onClose())
                .bounds(cx-60, cy, 120, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("screen.db_renewed.gui.back"), b -> this.onClose())
                .bounds(cx-60, cy+24, 120, 20).build());
    }
    @Override public void onClose(){ this.minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen(){ return false; }
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(this.font,this.title,this.width/2,20,0xFFFFFF);
    }
}
