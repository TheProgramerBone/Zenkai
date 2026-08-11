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
        boolean on = state.getAsBoolean();
        boolean hovered = this.active && isMouseOver(mouseX, mouseY);

        // Teñido, no velo: btn_wide tiene las esquinas recortadas y un g.fill sobre el rect
        // dejaba muescas oscuras alrededor del botón. Mismo criterio que PanelButton y
        // MinusIconButton — el estado se expresa sobre la textura, nunca sobre el fondo.
        //
        // ON y OFF comparten marco a propósito: son dos estados de UNA cosa, no dos botones.
        if (!this.active) g.setColor(0.55f, 0.55f, 0.55f, 0.8f);
        else if (!on) g.setColor(0.72f, 0.72f, 0.72f, 1f);
        else if (hovered) g.setColor(1.15f, 1.15f, 1.15f, 1f);
        NineSlice.button(g, getX(), getY(), getWidth(), getHeight());
        g.setColor(1f, 1f, 1f, 1f);

        int color = !this.active ? ZenkaiPalette.TEXT_OFF
                : on ? ZenkaiPalette.OK
                : ZenkaiPalette.TEXT_DIM;

        var font = Minecraft.getInstance().font;
        // Sombra SÍ: el texto va sobre la textura del botón, que es oscura y ornamentada.
        // Explícito y no drawCenteredString para que la decisión quede escrita.
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