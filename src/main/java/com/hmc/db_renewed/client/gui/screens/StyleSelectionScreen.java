package com.hmc.db_renewed.client.gui.screens;

import com.hmc.db_renewed.DragonBlockRenewed;
import com.hmc.db_renewed.core.network.ChooseStylePacket;
import com.hmc.db_renewed.core.network.feature.Style;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

public class StyleSelectionScreen extends Screen {

    private static final int BUTTON_WIDTH = 140;
    private static final int BUTTON_HEIGHT = 20;

    public StyleSelectionScreen() {
        super(Component.translatable("screen." + DragonBlockRenewed.MOD_ID + ".choose_style.title"));
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int startY  = this.height / 2 - 40;
        int spacing = 24;

        addStyleButton(Style.WARRIOR,        centerX - BUTTON_WIDTH / 2, startY);
        addStyleButton(Style.MARTIAL_ARTIST, centerX - BUTTON_WIDTH / 2, startY + spacing);
        addStyleButton(Style.SPIRITUALIST,   centerX - BUTTON_WIDTH / 2, startY + spacing * 2);
    }

    private void addStyleButton(Style style, int x, int y) {
        String keyBase = "screen." + DragonBlockRenewed.MOD_ID + ".style." + style.name().toLowerCase();

        Component label   = Component.translatable(keyBase);
        Component tooltip = Component.translatable(keyBase + ".desc");

        Button btn = Button.builder(label, b -> onStyleChosen(style))
                .bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT)
                .tooltip(Tooltip.create(tooltip))
                .build();

        this.addRenderableWidget(btn);
    }

    private void onStyleChosen(Style style) {
        // Avisar al servidor
        PacketDistributor.sendToServer(new ChooseStylePacket(style));

        // Cerrar pantalla; a partir de ahora V abrirá stats
        Minecraft.getInstance().setScreen(null);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}