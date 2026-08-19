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
 * Gemelo de PlusIconButton para la operación inversa: bajar un nivel de habilidad.
 * EXISTE PORQUE EL SHIFT-CLICK NO SE VE Y ADEMÁS DESAPARECÍA. Olvidar colgaba del botón +,
 * que layoutRows apaga al llegar al nivel máximo — justo el momento en que uno quiere
 * rectificar. Un botón propio, con su propia condición de visibilidad, no puede heredar
 * el apagado del otro.
 * ⚠ Necesita textures/gui/btn_x.png y btn_x_highlight.png (12x12).
 */
public class XIconButton extends AbstractButton {

    private static final ResourceLocation TEX_NORMAL =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/btn_x.png");

    private static final ResourceLocation TEX_HOVER =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/btn_x_highlight.png");

    private final Runnable onClick;

    public XIconButton(int x, int y, Runnable onClick) {
        super(x, y, 12, 12, Component.empty());
        this.onClick = Objects.requireNonNull(onClick);
    }

    @Override
    public void onPress() { onClick.run(); }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        ResourceLocation tex = (this.active && this.isHoveredOrFocused()) ? TEX_HOVER : TEX_NORMAL;
        if (!this.active) g.setColor(0.45F, 0.45F, 0.45F, 1.0F);
        g.blit(tex, this.getX(), this.getY(), 0, 0, this.width, this.height, 12, 12);
        if (!this.active) g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    @Override
    protected void updateWidgetNarration(@NotNull NarrationElementOutput out) {}
}