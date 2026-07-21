package com.hmc.zenkai.client.gui.buttons;

import com.hmc.zenkai.Zenkai;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class PlusIconButton extends AbstractButton {

    // Ruta directa al PNG (16x16 recomendado)
    private static final ResourceLocation TEX_NORMAL =
            ResourceLocation.fromNamespaceAndPath(
                    Zenkai.MOD_ID,
                    "textures/gui/btn_plus.png"
            );

    private static final ResourceLocation TEX_HOVER =
            ResourceLocation.fromNamespaceAndPath(
                    Zenkai.MOD_ID,
                    "textures/gui/btn_plus_highlight.png"
            );

    // Acción al pulsar
    private final Runnable onClick;

    public PlusIconButton(int x, int y, Runnable onClick) {
        super(x, y, 12, 12, Component.empty());
        this.onClick = Objects.requireNonNull(onClick);
    }

    @Override
    public void onPress() {
        onClick.run();
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        ResourceLocation tex = (this.active && this.isHoveredOrFocused()) ? TEX_HOVER : TEX_NORMAL;
        if (!this.active) g.setColor(0.45F, 0.45F, 0.45F, 1.0F); // sin TP/MND: apagado
        g.blit(tex, this.getX(), this.getY(), 0, 0, this.width, this.height, 12, 12);
        if (!this.active) g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    @Override
    protected void updateWidgetNarration(@NotNull NarrationElementOutput narrationElementOutput) {
    }
}
