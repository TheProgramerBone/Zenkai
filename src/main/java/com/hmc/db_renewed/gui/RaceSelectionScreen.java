package com.hmc.db_renewed.gui;

import com.hmc.db_renewed.DragonBlockRenewed;
import com.hmc.db_renewed.network.stats.ChooseRacePacket;
import com.hmc.db_renewed.network.stats.Race;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

public class RaceSelectionScreen extends Screen {

    private static final int BUTTON_WIDTH = 120;
    private static final int BUTTON_HEIGHT = 20;

    public RaceSelectionScreen() {
        super(Component.translatable("screen." + DragonBlockRenewed.MOD_ID + ".choose_race.title"));
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int startY  = this.height / 2 - 50;
        int spacing = 24;

        addRaceButton(Race.HUMAN,    centerX - BUTTON_WIDTH / 2, startY);
        addRaceButton(Race.SAIYAN,   centerX - BUTTON_WIDTH / 2, startY + spacing);
        addRaceButton(Race.NAMEKIAN, centerX - BUTTON_WIDTH / 2, startY + spacing * 2);
        addRaceButton(Race.ARCOSIAN, centerX - BUTTON_WIDTH / 2, startY + spacing * 3);
        addRaceButton(Race.MAJIN,    centerX - BUTTON_WIDTH / 2, startY + spacing * 4);
    }

    private void addRaceButton(Race race, int x, int y) {
        String keyBase = "screen." + DragonBlockRenewed.MOD_ID + ".race." + race.name().toLowerCase();

        Component label   = Component.translatable(keyBase);          // nombre
        Component tooltip = Component.translatable(keyBase + ".desc"); // descripción

        Button btn = Button.builder(label, b -> onRaceChosen(race))
                .bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT)
                .tooltip(Tooltip.create(tooltip))
                .build();

        this.addRenderableWidget(btn);
    }

    private void onRaceChosen(Race race) {
        // Avisar al servidor de la raza elegida
        PacketDistributor.sendToServer(new ChooseRacePacket(race));

        // IMPORTANTE: ya no marcamos hasChosenRace aquí,
        // eso se hará cuando elija estilo.

        // Abrir pantalla de selección de estilo
        Minecraft.getInstance().setScreen(new StyleSelectionScreen());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}