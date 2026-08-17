package com.hmc.zenkai.client.gui.buttons;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.gui.ZenkaiPalette;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Pestaña con ícono del atlas textures/gui/icons.png (270x270), sin texto.
 * El nombre de la pestaña va como tooltip (setTooltip desde fuera si se quiere).
 * Estados: seleccionada -> marcador inferior + ícono pleno; hover -> velo claro;
 * normal -> ícono ligeramente atenuado (para qué la activa destaque).
 */
public class TabIconButton extends AbstractButton {

    private static final ResourceLocation ATLAS =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/icons.png");
    private static final int ATLAS_W = 256;
    private static final int ATLAS_H = 256;

    private final int u, v;               // esquina del ícono dentro del atlas
    private final int iconSize;           // celda cuadrada (px en el atlas y en pantalla)
    private final Supplier<Boolean> selected;
    private final Runnable onClick;

    public TabIconButton(int x, int y, int iconSize, int u, int v,
                         Component name, Supplier<Boolean> selected, Runnable onClick) {
        super(x, y, iconSize, iconSize, name);
        this.iconSize = iconSize;
        this.u = u;
        this.v = v;
        this.selected = selected;
        this.onClick = Objects.requireNonNull(onClick);
    }

    @Override
    public void onPress() {
        onClick.run();
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.blit(ATLAS, getX(), getY(), u, v, iconSize, iconSize, ATLAS_W, ATLAS_H);

        if (!selected.get() && isHoveredOrFocused()) {
            // Hover: velo claro.
            g.fill(getX(), getY(), getX() + iconSize, getY() + iconSize, ZenkaiPalette.HOVER_VEIL);
        }

        if (selected.get()) {
            // Marcador bajo la pestaña activa.
            g.fill(getX(), getY() + iconSize + 1, getX() + iconSize, getY() + iconSize + 2,
                    ZenkaiPalette.TEXT);
        }
    }
    @Override
    protected void updateWidgetNarration(@NotNull NarrationElementOutput out) {
    }
}