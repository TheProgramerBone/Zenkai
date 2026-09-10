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
 * Badge redondo con un checkmark verde — celda (0,60) de icons.png, la misma que
 * FriendlyFireIconButton pinta para su estado OFF/protegido y que PartyScreen ya reusa como
 * "Confirmar" de su popup de PartyConfig (CHECK_U/CHECK_V). Hermano de BackIconButton (20,60,
 * la X roja de al lado en el mismo atlas) para el caso contrario: confirmar en vez de cancelar.
 * Mismo idioma que el resto de esta familia (PlayIconButton/BackIconButton): tamaño configurable,
 * sin textura de hover propia, el brillo sale de teñir el propio blit.
 */
public class AcceptIconButton extends AbstractButton {

    public static final int SIZE = 16;

    private static final ResourceLocation ATLAS =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/icons.png");
    private static final int ATLAS_W = 256;
    private static final int ATLAS_H = 256;
    private static final int CELL = 20;
    private static final int U = 0, V = 60;

    private final int size;
    private final Runnable onClick;

    public AcceptIconButton(int x, int y, Runnable onClick) {
        this(x, y, SIZE, onClick);
    }

    public AcceptIconButton(int x, int y, int size, Runnable onClick) {
        super(x, y, size, size, Component.empty());
        this.size = size;
        this.onClick = Objects.requireNonNull(onClick);
    }

    @Override
    public void onPress() { onClick.run(); }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (!this.active) g.setColor(0.55F, 0.55F, 0.55F, 1.0F);
        else if (isHoveredOrFocused()) g.setColor(1.15F, 1.15F, 1.15F, 1.0F);
        g.blit(ATLAS, this.getX(), this.getY(), size, size,
                (float) U, (float) V, CELL, CELL, ATLAS_W, ATLAS_H);
        g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    @Override
    protected void updateWidgetNarration(@NotNull NarrationElementOutput out) {
        defaultButtonNarrationText(out);
    }
}
