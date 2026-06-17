package com.hmc.zenkai.client.gui.widgets;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.function.Supplier;

public class ColorBoxButton extends AbstractWidget {

    private final Supplier<Integer> colorSupplier; // devuelve el color ARGB actual
    private final Supplier<Boolean> activeSupplier; // true si el picker está abierto
    private final Runnable onClick;

    public ColorBoxButton(int x, int y, int w, int h,
                          Supplier<Integer> colorSupplier,
                          Supplier<Boolean> activeSupplier,
                          Runnable onClick) {
        super(x, y, w, h, Component.empty());
        this.colorSupplier  = colorSupplier;
        this.activeSupplier = activeSupplier;
        this.onClick        = onClick;
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int color       = colorSupplier.get();
        boolean active  = activeSupplier.get();

        int bx = getX();
        int by = getY();
        int bw = getWidth();
        int bh = getHeight();

        // Borde: naranja si activo, blanco si no
        int borderColor = active ? 0xFFFFAA00 : 0xFFFFFFFF;
        g.fill(bx - 1, by - 1, bx + bw + 1, by + bh + 1, borderColor);

        // Color interior
        g.fill(bx, by, bx + bw, by + bh, 0xFF000000 | (color & 0xFFFFFF));
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        onClick.run();
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput out) {}
}