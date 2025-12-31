package com.hmc.db_renewed.client.gui.buttons;

import com.hmc.db_renewed.DragonBlockRenewed;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

public class ArrowIconButton extends AbstractButton {

    public enum Dir { LEFT, RIGHT }

    private final ResourceLocation texNormal;
    private final ResourceLocation texHover;
    private final Runnable onClick;

    public ArrowIconButton(int x, int y, Dir dir, Runnable onClick) {
        // MISMO super que tu PlusIconButton
        super(x, y, 12, 12, Component.empty());
        this.onClick = Objects.requireNonNull(onClick);

        String base = (dir == Dir.LEFT) ? "btn_arrow_left" : "btn_arrow_right";

        this.texNormal = ResourceLocation.fromNamespaceAndPath(
                DragonBlockRenewed.MOD_ID, "textures/gui/" + base + ".png"
        );
        this.texHover = ResourceLocation.fromNamespaceAndPath(
                DragonBlockRenewed.MOD_ID, "textures/gui/" + base + "_highlight.png"
        );
    }

    @Override
    public void onPress() {
        onClick.run();
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // En vez de isHoveredOrFocused(), usa esto:
        ResourceLocation tex = this.isMouseOver(mouseX, mouseY) ? texHover : texNormal;

        g.blit(
                tex,
                this.getX(), this.getY(),
                0, 0,
                this.width, this.height,
                12, 12
        );
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput out) {
    }
}
