package com.hmc.zenkai.client.gui.buttons;

import com.hmc.zenkai.Zenkai;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * El + del banco de scouter. NO es PlusIconButton: aquel mide 12x12 y comparte textura con la
 * pantalla de estadísticas, donde el + significa "gastar TP". Aquí significa "instalar una
 * mejora" y vive en filas de 20 px, así que tiene asset propio y tamaño propio.
 *
 * ATLAS btn_plus_bench.png — 48x16, tres celdas de 16x16 en horizontal:
 *   u=0   normal
 *   u=16  hover
 *   u=32  pulsado (ratón abajo sobre el botón)
 * El estado APAGADO no gasta celda: es la celda normal multiplicada por gris con setColor,
 * igual que hace PanelButton. Dibujar un cuarto estado sería pedir arte para algo que el
 * shader resuelve.
 */
public class BenchPlusButton extends AbstractButton {

    public static final int SIZE = 16;

    private static final ResourceLocation TEX =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/btn_plus_bench.png");
    private static final int ATLAS_W = 48;
    private static final int ATLAS_H = 16;

    private final Runnable onClick;
    private boolean held;

    public BenchPlusButton(int x, int y, Runnable onClick) {
        super(x, y, SIZE, SIZE, Component.empty());
        this.onClick = Objects.requireNonNull(onClick);
    }

    @Override
    public void onPress() { onClick.run(); }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        boolean handled = super.mouseClicked(mx, my, button);
        if (handled) held = true;
        return handled;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        held = false;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    protected void renderWidget(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        boolean hovered = this.active && isMouseOver(mouseX, mouseY);
        int u = !this.active ? 0 : (held && hovered ? 32 : hovered ? 16 : 0);

        // enableBlend + setColor blanco ANTES del blit: sin esto el icono hereda el color de
        // lo último que se haya teñido en el frame y sale transparente o gris.
        RenderSystem.enableBlend();
        if (!this.active) g.setColor(0.45f, 0.45f, 0.45f, 1f);
        else g.setColor(1f, 1f, 1f, 1f);

        g.blit(TEX, getX(), getY(), u, 0, SIZE, SIZE, ATLAS_W, ATLAS_H);

        g.setColor(1f, 1f, 1f, 1f);
    }

    @Override
    protected void updateWidgetNarration(@NotNull NarrationElementOutput out) {
        defaultButtonNarrationText(out);
    }
}