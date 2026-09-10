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
 * Badge redondo "✕" (círculo rojo + aspa blanca) para Back/Cancel — misma celda de
 * icons.png (20,60) que ya usa FriendlyFireIconButton para su estado ON/peligro
 * (tools/gen_party_icons.py), reaprovechada aquí porque el glifo ya es exactamente lo que
 * hace falta: pedido explícito del usuario tras ver capturas reales, la X plana de
 * btn_x.png (familia de bisel de dos tonos, ver XIconButton) desentonaba al lado del badge
 * "▶" pintado/sombreado de PlayIconButton en la misma fila de Back/Start — dos familias
 * visuales distintas conviviendo en el mismo botón de acción. Mismo idioma que PlayIconButton
 * en todo lo demás: tamaño configurable, sin textura de hover propia (el brillo sale de teñir
 * el propio blit).
 */
public class BackIconButton extends AbstractButton {

    /** Tamaño por defecto — el que ya usaba XIconButton para sus usos pequeños (stepper -). */
    public static final int SIZE = 12;

    private static final ResourceLocation ATLAS =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/icons.png");
    private static final int ATLAS_W = 256;
    private static final int ATLAS_H = 256;
    private static final int CELL = 20;
    private static final int U = 20, V = 60;

    private final int size;
    private final Runnable onClick;

    public BackIconButton(int x, int y, Runnable onClick) {
        this(x, y, SIZE, onClick);
    }

    public BackIconButton(int x, int y, int size, Runnable onClick) {
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
