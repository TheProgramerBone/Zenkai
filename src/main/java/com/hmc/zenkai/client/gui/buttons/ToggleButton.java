package com.hmc.zenkai.client.gui.buttons;

import com.hmc.zenkai.client.gui.NineSlice;
import com.hmc.zenkai.client.gui.ZenkaiPalette;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Interruptor ON/OFF con el marco del mod.
 * Reemplaza los Button.builder() grises de ClientConfigScreen, que eran el único punto del
 * menú con widgets vanilla y cantaban muchísimo al lado del panel beige.
 * El estado se lee de un supplier en cada frame en vez de guardarse dentro: la pantalla de
 * config trabaja sobre un buffer "staged" que se descarta al salir sin guardar, y un botón con
 * estado propio se habría desincronizado de ese buffer en cuanto se añadiera un "restaurar
 * valores por defecto".
 * El color hace el trabajo pesado (verde = encendido, gris = apagado) porque a tamaño 54x20 el
 * texto ON/OFF es solo dos glifos y a GUI Scale bajo se lee mal.
 */
public class ToggleButton extends AbstractButton {

    public static final int W = 54;
    public static final int H = 20;

    private final BooleanSupplier state;
    private final Consumer<Boolean> onChange;

    public ToggleButton(int x, int y, int w, int h, BooleanSupplier state, Consumer<Boolean> onChange) {
        super(x, y, w, h, Component.empty());
        this.state = state;
        this.onChange = onChange;
    }

    public ToggleButton(int x, int y, BooleanSupplier state, Consumer<Boolean> onChange) {
        this(x, y, W, H, state, onChange);
    }

    @Override
    public void onPress() { onChange.accept(!state.getAsBoolean()); }

    @Override
    public @NotNull Component getMessage() {
        return Component.translatable(state.getAsBoolean() ? "options.on" : "options.off");
    }

    @Override
    protected void renderWidget(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        NineSlice.button(g, getX(), getY(), getWidth(), getHeight());

        boolean on = state.getAsBoolean();
        boolean hovered = this.active && isMouseOver(mouseX, mouseY);

        // Velo del estado apagado: oscurece el interior sin tocar el marco, que sigue siendo
        // el mismo objeto. Así ON y OFF se leen como dos estados de UNA cosa y no como dos
        // botones distintos.
        if (!on) {
            g.fill(getX() + 2, getY() + 2, getX() + getWidth() - 2, getY() + getHeight() - 2, 0x60201810);
        }
        if (hovered) {
            g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), ZenkaiPalette.HOVER_VEIL);
        }

        int color = !this.active ? ZenkaiPalette.TEXT_OFF
                : on ? ZenkaiPalette.OK
                : ZenkaiPalette.TEXT_DIM;

        g.drawCenteredString(Minecraft.getInstance().font, getMessage(),
                getX() + getWidth() / 2,
                getY() + (getHeight() - 8) / 2,
                color);
    }

    @Override
    protected void updateWidgetNarration(@NotNull NarrationElementOutput out) {
        defaultButtonNarrationText(out);
    }
}