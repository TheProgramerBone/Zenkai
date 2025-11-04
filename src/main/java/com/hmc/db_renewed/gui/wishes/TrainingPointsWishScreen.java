package com.hmc.db_renewed.gui.wishes;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class TrainingPointsWishScreen extends Screen {
    private final Screen parent;
    public TrainingPointsWishScreen(Screen parent) {
        super(Component.translatable("screen.db_renewed.wish.training_points"));
        this.parent = parent;
    }
    @Override protected void init() {
        int cx = this.width/2, cy = this.height/2;
        this.addRenderableWidget(Button.builder(Component.translatable("screen.db_renewed.gui.confirm"), b -> this.onClose())
                .bounds(cx-60, cy, 120, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("screen.db_renewed.gui.back"), b -> this.onClose())
                .bounds(cx-60, cy+24, 120, 20).build());
    }
    @Override public void onClose(){
        assert this.minecraft != null;
        this.minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen(){ return false; }
    @Override public void render(GuiGraphics g,int x,int y,float p){
        this.renderBackground(g,x,y,p);
        super.render(g,x,y,p);
        g.drawCenteredString(this.font,this.title,this.width/2,20,0xFFFFFF);
    }
}
