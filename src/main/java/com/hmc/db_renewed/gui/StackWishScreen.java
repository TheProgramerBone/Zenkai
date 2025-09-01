package com.hmc.db_renewed.gui;

import com.hmc.db_renewed.network.ConfirmWishPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class StackWishScreen extends AbstractContainerScreen<StackWishMenu> {
    private static final ResourceLocation BG_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("db_renewed", "textures/gui/stack_wish.png");

    private final ShenlongWishScreen parent;

    public StackWishScreen(StackWishMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 176;
        this.imageHeight = 133;
        this.parent = null;
    }

    @Override
    protected void init() {
        super.init();

        int centerX = (this.width - this.imageWidth) / 2;
        int centerY = (this.height - this.imageHeight) / 2;

        // Botón Confirmar
        this.addRenderableWidget(
                Button.builder(
                        Component.translatable("screen.db_renewed.confirm"), btn -> {
                            ItemStack chosen = this.menu.getChosenItem().copy();
                            if (!chosen.isEmpty()) {
                                if (Minecraft.getInstance().getConnection() != null) {
                                    Minecraft.getInstance().getConnection().send(new ConfirmWishPayload(chosen));
                                } else {
                                    assert Minecraft.getInstance().player != null;
                                    Minecraft.getInstance().player.displayClientMessage(Component.translatable("message.db_renewed.no_connection"), false);
                                }
                            }
                            assert Minecraft.getInstance().player != null;
                            Minecraft.getInstance().player.closeContainer();
                            this.onClose();
                        }).bounds(centerX + 20, centerY + 135, 60, 20).build()
        );

        // Botón Volver
        this.addRenderableWidget(
                Button.builder(
                        Component.translatable("screen.db_renewed.back"), btn -> {
                            Minecraft.getInstance().setScreen(
                                    Objects.requireNonNullElseGet(this.parent, ShenlongWishScreen::new)
                            );
                        }).bounds(centerX + 100, centerY + 135, 60, 20).build()
        );
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int centerX = (this.width - this.imageWidth) / 2;
        int centerY = (this.height - this.imageHeight) / 2;
        guiGraphics.blit(BG_TEXTURE, centerX, centerY, 0, 0, this.imageWidth, this.imageHeight);
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawCenteredString(this.font, this.title, this.imageWidth / 2, 6, 0x404040);
        guiGraphics.drawString(this.font, this.playerInventoryTitle, 8, this.imageHeight - 96 + 2, 0x404040, false);
    }
}