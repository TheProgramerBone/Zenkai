package com.hmc.zenkai.client.gui.buttons;

import com.hmc.zenkai.Zenkai;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Gemelo de PlusIconButton para devolver un punto de atributo.
 * Usa btn_minus.png / btn_minus_highlight.png (12x12, mismo formato que btn_plus). Si el asset
 * no está, el fallback dibuja una barra horizontal: prefiero un menos feo a un hueco invisible,
 * porque un botón que no se ve es un botón que no existe para el jugador.
 * A diferencia del más, el menos se APAGA cuando no queda nada que devolver en ese atributo
 * (los puntos de base racial no se venden). Se apaga en vez de ocultarse para que la columna no
 * baile: las seis filas mantienen la misma anchura tenga o no puntos invertidos cada una.
 */
public class MinusIconButton extends AbstractButton {

    public static final int SIZE = 12;

    private static final ResourceLocation TEX =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/btn_minus.png");
    private static final ResourceLocation TEX_HL =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/btn_minus_highlight.png");

    private final Runnable onClick;

    public MinusIconButton(int x, int y, Runnable onClick) {
        super(x, y, SIZE, SIZE, Component.empty());
        this.onClick = Objects.requireNonNull(onClick);
    }

    @Override
    public void onPress() { onClick.run(); }

    @Override
    protected void renderWidget(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        boolean hovered = this.active && isMouseOver(mouseX, mouseY);
        if (!this.active) g.setColor(0.55f, 0.55f, 0.55f, 0.75f);
        g.blit(hovered ? TEX_HL : TEX, getX(), getY(), 0, 0, SIZE, SIZE, SIZE, SIZE);
        g.setColor(1f, 1f, 1f, 1f);
    }

    @Override
    protected void updateWidgetNarration(@NotNull NarrationElementOutput out) {
        defaultButtonNarrationText(out);
    }
}