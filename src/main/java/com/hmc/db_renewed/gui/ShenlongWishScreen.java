package com.hmc.db_renewed.gui;

import com.hmc.db_renewed.gui.wishes.*;
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

    private Button stackWishButton;
    private Button revivePetButton;
    private Button revivePlayerButton;
    private Button enchantVillagerButton;
    private Button immortalButton;
    private Button trainingPointsButton;

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        int y = centerY - 20;
        int w = 160, h = 20;
        this.stackWishButton = addRenderableWidget(Button.builder(
                Component.translatable("screen.db_renewed.option.stack"),
                btn -> {
                    if (Minecraft.getInstance().getConnection() != null) {
                        Minecraft.getInstance().getConnection().send(new OpenStackWishPayload());
                    }
                }
        ).bounds(centerX - w / 2, y, w, h)
                .build());

        y += 24;

        // Revivir Jugador
        this.revivePlayerButton = addRenderableWidget(
                Button.builder(
                        Component.translatable("screen.db_renewed.wish.revive_player"),
                        b -> {
                            assert this.minecraft != null;
                            this.minecraft.setScreen(new RevivePlayerWishScreen(this));
                        }
                ).bounds(centerX - w / 2, y, w, h).build()
        );
        y += 24;

        // Aldeano con Encantamiento
        this.enchantVillagerButton = addRenderableWidget(
                Button.builder(
                        Component.translatable("screen.db_renewed.wish.enchant_villager"),
                        b -> {
                            assert this.minecraft != null;
                            this.minecraft.setScreen(new EnchantVillagerWishScreen(this));
                        }
                ).bounds(centerX - w / 2, y, w, h).build()
        );
        y += 24;

        // Ser Inmortal (efecto)
        this.immortalButton = addRenderableWidget(
                Button.builder(
                        Component.translatable("screen.db_renewed.wish.immortal"),
                        b -> {
                            assert this.minecraft != null;
                            this.minecraft.setScreen(new ImmortalWishScreen(this));
                        }
                ).bounds(centerX - w / 2, y, w, h).build()
        );
        y += 24;

        // Training Points
        this.trainingPointsButton = addRenderableWidget(
                Button.builder(
                        Component.translatable("screen.db_renewed.wish.training_points"),
                        b -> this.minecraft.setScreen(new TrainingPointsWishScreen(this))
                ).bounds(centerX - w / 2, y, w, h).build()
        );
    }

    @Override
    public void tick() {
        super.tick();
        var mc = Minecraft.getInstance();
        boolean full = mc.player != null && mc.player.getInventory().getFreeSlot() == -1;

        if (this.stackWishButton != null) {
            this.stackWishButton.active = !full;

            if (full) {
                this.stackWishButton.setTooltip(Tooltip.create(
                        Component.translatable("screen.db_renewed.need_inventory_space")));
            } else {
                this.stackWishButton.setTooltip(null);
            }
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xFFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
