package com.hmc.db_renewed.client.gui;

import com.hmc.db_renewed.common.player.RaceDataHandler;
import com.hmc.db_renewed.common.race.Race;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.opengl.GL11;

public class RaceSelectionScreen extends Screen {

    private final Player player;

    private Race selectedRace = Race.HUMAN;

    public RaceSelectionScreen() {
        super(Component.literal("Select Your Race"));
        this.player = Minecraft.getInstance().player;
    }

    private String formatRaceName(Race race) {
        String name = race.name().toLowerCase();
        return "Race " + Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int buttonWidth = 20;
        int labelWidth = 100;

        // Flecha izquierda
        Button raceLeftButton = Button.builder(Component.literal("<"), btn -> {
            int currentIndex = selectedRace.ordinal();
            int newIndex = (currentIndex - 1 + Race.values().length) % Race.values().length;
            selectedRace = Race.values()[newIndex];
        }).pos(centerX - labelWidth / 2 - buttonWidth - 5, centerY).size(buttonWidth, 20).build();

        // Flecha derecha
        Button raceRightButton = Button.builder(Component.literal(">"), btn -> {
            int currentIndex = selectedRace.ordinal();
            int newIndex = (currentIndex + 1) % Race.values().length;
            selectedRace = Race.values()[newIndex];
        }).pos(centerX + labelWidth / 2 + 5, centerY).size(buttonWidth, 20).build();

        // Confirmar
        Button confirmButton = Button.builder(Component.literal("Confirm"), btn -> {
            RaceDataHandler.save(this.player, selectedRace, true);
            this.player.sendSystemMessage(Component.literal("You have selected the " + formatRaceName(selectedRace)));
            this.onClose();
        }).pos(centerX - 50, centerY + 40).size(100, 20).build();

        this.addRenderableWidget(raceLeftButton);
        this.addRenderableWidget(raceRightButton);
        this.addRenderableWidget(confirmButton);
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {

        RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT, true);

        RenderSystem.disableDepthTest();

        graphics.fill(0, 0, this.width, this.height, 0xAA000000);

        // Título
        graphics.drawCenteredString(this.font, formatRaceName(selectedRace), this.width / 2, this.height / 2 - 20, 0xFFFFFF);

        // Renderiza el modelo del jugador
        int modelX = 160;      // Posición en X de la entidad
        int modelY = 140;     // Posición en Y (altura base del modelo)
        int scale = 50;       // Tamaño del modelo renderizado

        InventoryScreen.renderEntityInInventoryFollowsMouse(
                graphics,
                0, 0,                  // Coordenadas base GUI (puedes ajustar si usas contenedor)
                modelX, modelY,               // Posición donde se dibuja el modelo
                scale,
                0.0f,                  // yOffset (puedes ajustar para subir/bajar el modelo)
                (float)(mouseX - modelX),     // Movimiento horizontal del mouse
                (float)(mouseY - modelY),     // Movimiento vertical del mouse
                this.player
        );
        super.render(graphics, mouseX, mouseY, partialTicks);
    }
}