package com.hmc.zenkai.client.gui.buttons;

import com.hmc.zenkai.client.gui.NineSlice;
import com.hmc.zenkai.client.gui.ZenkaiTechPalette;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Botón de la familia TECNOLÓGICA. Es el gemelo de PanelButton, no su sustituto: PanelButton
 * usa btn_wide.png, que tiene esquinas recortadas y brillo dorado y pertenece a las pantallas
 * de personaje. Sobre una carcasa blanca de laboratorio ese dorado canta, y al revés también.
 *
 * Mismo tamaño estándar que PanelButton (84x20) para que un pie de página se vea igual de
 * ordenado en las dos familias, y mismo criterio: se APAGA en vez de ocultarse, tiñendo la
 * textura con setColor y no pintando un velo encima — un g.fill sobre un botón con bisel deja
 * los cuatro bordes fuera del velo.
 *
 * Dos jerarquías, distinguidas solo por el color del texto:
 *   PRIMARY   → la acción que el jugador vino a hacer. Cian.
 *   SECONDARY → la salida (Cancelar, Volver). Texto claro.
 */
public class TechButton extends AbstractButton {

    public static final int W = 84;
    public static final int H = 20;

    public enum Kind { PRIMARY, SECONDARY }

    private final Runnable onClick;
    private final Kind kind;

    public TechButton(int x, int y, int w, int h, Component message, Kind kind, Runnable onClick) {
        super(x, y, w, h, message);
        this.onClick = Objects.requireNonNull(onClick);
        this.kind = kind;
    }

    public static TechButton primary(int x, int y, Component message, Runnable onClick) {
        return new TechButton(x, y, W, H, message, Kind.PRIMARY, onClick);
    }

    public static TechButton secondary(int x, int y, Component message, Runnable onClick) {
        return new TechButton(x, y, W, H, message, Kind.SECONDARY, onClick);
    }

    @Override
    public void onPress() { onClick.run(); }

    @Override
    protected void renderWidget(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        boolean hovered = this.active && isMouseOver(mouseX, mouseY);

        if (!this.active) g.setColor(0.6f, 0.6f, 0.6f, 0.85f);
        else if (hovered) g.setColor(1.15f, 1.15f, 1.15f, 1f);
        NineSlice.techButton(g, getX(), getY(), getWidth(), getHeight());
        g.setColor(1f, 1f, 1f, 1f);

        int color = !this.active
                ? ZenkaiTechPalette.DIM_ON_SCREEN
                : hovered ? ZenkaiTechPalette.CYAN_HI
                : (kind == Kind.PRIMARY ? ZenkaiTechPalette.CYAN
                : ZenkaiTechPalette.TEXT_ON_SCREEN);

        var font = Minecraft.getInstance().font;
        // Sombra SÍ: el texto va sobre el acero del botón, que es oscuro.
        g.drawString(font, getMessage(),
                getX() + getWidth() / 2 - font.width(getMessage()) / 2,
                getY() + (getHeight() - 8) / 2,
                color, true);
    }

    @Override
    protected void updateWidgetNarration(@NotNull NarrationElementOutput out) {
        defaultButtonNarrationText(out);
    }
}