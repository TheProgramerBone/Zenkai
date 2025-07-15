package com.hmc.db_renewed.client.gui;

import com.hmc.db_renewed.common.player.RaceDataHandler;
import com.hmc.db_renewed.common.race.Race;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;

public class RaceSelectionScreen extends Screen {

    private final Player player;
    private Race selectedRace = Race.HUMAN;

    public RaceSelectionScreen() {
        super(Component.literal("Select Your Race"));
        this.player = Minecraft.getInstance().player;
    }

    private String formatRaceName(Race race) {
        String[] parts = race.name().toLowerCase().split("_");
        StringBuilder builder = new StringBuilder("Race: ");
        for (String part : parts) {
            builder.append(Character.toUpperCase(part.charAt(0)))
                    .append(part.substring(1))
                    .append(" ");
        }
        return builder.toString().trim();
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        int labelOffsetY = 70; // Texto está debajo del modelo
        int buttonOffsetY = 100;

        int labelWidth = 120;
        int arrowWidth = 20;
        int arrowSpacing = 5;

        // Botón Izquierda
        this.addRenderableWidget(Button.builder(Component.literal("<"), btn -> {
            int index = (selectedRace.ordinal() - 1 + Race.values().length) % Race.values().length;
            selectedRace = Race.values()[index];
        }).pos(centerX - labelWidth / 2 - arrowWidth - arrowSpacing, centerY + labelOffsetY).size(arrowWidth, 20).build());

        // Botón Derecha
        this.addRenderableWidget(Button.builder(Component.literal(">"), btn -> {
            int index = (selectedRace.ordinal() + 1) % Race.values().length;
            selectedRace = Race.values()[index];
        }).pos(centerX + labelWidth / 2 + arrowSpacing, centerY + labelOffsetY).size(arrowWidth, 20).build());

        // Botón Confirmar
        this.addRenderableWidget(Button.builder(Component.literal("Confirm"), btn -> {
            RaceDataHandler.save(player, selectedRace, true);
            player.sendSystemMessage(Component.literal("You have selected the " + formatRaceName(selectedRace)));
            this.onClose();
        }).pos(centerX - 50, centerY + buttonOffsetY).size(100, 20).build());
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        super.render(graphics, mouseX, mouseY, partialTicks);

        int modelX = centerX;
        int modelY = centerY - 40;
        int scale = 50;

        float xRot = (float)(mouseX - modelX);
        float yRot = (float)(mouseY - modelY);

        InventoryScreen.renderEntityInInventoryFollowsMouse(
                graphics,
                0, 0,
                modelX, modelY,
                scale,
                0.0f,
                xRot,
                yRot,
                this.player
        );

        graphics.drawCenteredString(
                this.font,
                formatRaceName(selectedRace),
                centerX,
                centerY + 20,
                0xFFFFFF
        );
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
