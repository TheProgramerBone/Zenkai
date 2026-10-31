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
 * Badge redondo azul con un «?» blanco: el botón que REPITE el tutorial de una pantalla
 * (SkillsTreeScreen, SkillTreeScreen, TrainingHubScreen). Antes era el {@code Button.builder("?")} de
 * vanilla, que desentonaba con el resto de botones de icono del mod; el usuario pidió que fuese de esta
 * familia. Blitea la celda (80,120) del atlas compartido ({@code tools/gen_help_badge_icon.py}) a
 * cualquier tamaño de destino, con el mismo truco de blit escalado y brillo al pasar el ratón que
 * {@link PlayIconButton} y {@link AtlasIconButton}.
 */
public class HelpIconButton extends AbstractButton {

    public static final int SIZE = 20;

    private static final ResourceLocation ATLAS =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/icons.png");
    private static final int ATLAS_W = 256;
    private static final int ATLAS_H = 256;
    private static final int CELL = 20;
    private static final int U = 80, V = 120;

    private final int size;
    private final Runnable onClick;

    public HelpIconButton(int x, int y, Runnable onClick) {
        this(x, y, SIZE, onClick);
    }

    public HelpIconButton(int x, int y, int size, Runnable onClick) {
        super(x, y, size, size, Component.translatable("screen.zenkai.tutorial.replay"));
        this.size = size;
        this.onClick = Objects.requireNonNull(onClick);
    }

    /**
     * Un «?» que solo despliega un texto largo al pasar el ratón (sin acción al pulsarlo). Lo usan las pantallas de
     * Training para dejar la descripción corta a la vista y el detalle de las reglas a un gesto de distancia.
     */
    public static HelpIconButton hint(int x, int y, int size, Component fullText) {
        HelpIconButton b = new HelpIconButton(x, y, size, () -> {});
        b.setTooltip(net.minecraft.client.gui.components.Tooltip.create(fullText));
        return b;
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
