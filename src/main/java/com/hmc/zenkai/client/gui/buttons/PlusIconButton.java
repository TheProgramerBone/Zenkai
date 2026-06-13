package com.hmc.zenkai.client.gui.buttons;

import com.hmc.zenkai.Zenkai;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

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
        // Elegimos textura según hover/focus
        ResourceLocation tex = this.isHoveredOrFocused() ? TEX_HOVER : TEX_NORMAL;

        // Dibuja el PNG completo (asumiendo 16x16)
        g.blit(
                tex,
                this.getX(), this.getY(),   // posición en pantalla
                0, 0,                       // u, v dentro del PNG
                this.width, this.height,    // ancho/alto a dibujar
                12, 12                      // ancho/alto del PNG real
        );
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
    }
}
