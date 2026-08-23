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
 * Botón de un ÚNICO ícono fijo del atlas textures/gui/icons.png (celda de 20px, mismo grid
 * que las pestañas), sin marco propio. Es la variante más simple de la familia — para un
 * ícono con dos estados (activado/desactivado) usa FriendlyFireIconButton en su lugar.
 * Reemplaza el uso de PanelButton (btn_wide) para "Invitar": ver la nota sobre btn_wide
 * dentro del contorno de common_screen en el javadoc de PartyScreen.
 */
public class AtlasIconButton extends AbstractButton {

    private static final ResourceLocation ATLAS =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/icons.png");
    private static final int ATLAS_W = 256;
    private static final int ATLAS_H = 256;
    private static final int SIZE = 20;

    private final int u, v;
    private final Runnable onClick;

    public AtlasIconButton(int x, int y, int u, int v, Runnable onClick) {
        super(x, y, SIZE, SIZE, Component.empty());
        this.u = u;
        this.v = v;
        this.onClick = Objects.requireNonNull(onClick);
    }

    @Override
    public void onPress() { onClick.run(); }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (!this.active) g.setColor(0.55F, 0.55F, 0.55F, 1.0F);
        else if (isHoveredOrFocused()) g.setColor(1.15F, 1.15F, 1.15F, 1.0F);
        g.blit(ATLAS, getX(), getY(), u, v, SIZE, SIZE, ATLAS_W, ATLAS_H);
        g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    @Override
    protected void updateWidgetNarration(@NotNull NarrationElementOutput out) {
        defaultButtonNarrationText(out);
    }
}
