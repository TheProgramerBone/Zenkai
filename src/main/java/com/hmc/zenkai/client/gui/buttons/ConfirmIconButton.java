package com.hmc.zenkai.client.gui.buttons;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Icono de acción destructiva con doble pulsación: la primera "arma" el botón (tinte rojo +
 * el tooltip cambia a la confirmación), la segunda ya ejecuta. Mismo patrón que la papelera de
 * TechniqueEditScreen (ver su campo {@code deleteArmed}), pero como widget reutilizable en vez
 * de un flag suelto copiado a mano en cada Screen — PartyScreen (Disolver party) es el segundo
 * sitio que lo necesita, y repetirlo una vez más ya era la señal de sacarlo de ahí.
 * El estado "armado" vive en la INSTANCIA del botón, no en un flag del Screen: como las
 * pantallas del mod recrean sus widgets desde cero en cada rebuild (ver PartyScreen.tick()),
 * un cambio de estado ajeno mientras está armado lo desarma solo sin lógica de reset aparte.
 */
public class ConfirmIconButton extends AbstractButton {

    private final ResourceLocation texNormal;
    private final ResourceLocation texHover;
    private final Component tooltipIdle;
    private final Component tooltipConfirm;
    private final Runnable onConfirm;
    private boolean armed = false;

    public ConfirmIconButton(int x, int y, int size,
                             ResourceLocation texNormal, ResourceLocation texHover,
                             Component tooltipIdle, Component tooltipConfirm, Runnable onConfirm) {
        super(x, y, size, size, Component.empty());
        this.texNormal = texNormal;
        this.texHover = texHover;
        this.tooltipIdle = tooltipIdle;
        this.tooltipConfirm = tooltipConfirm;
        this.onConfirm = Objects.requireNonNull(onConfirm);
        setTooltip(Tooltip.create(tooltipIdle));
    }

    @Override
    public void onPress() {
        if (!armed) {
            armed = true;
            setTooltip(Tooltip.create(tooltipConfirm));
            return;
        }
        onConfirm.run();
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        ResourceLocation tex = (this.active && this.isHoveredOrFocused()) ? texHover : texNormal;
        if (!this.active) g.setColor(0.45F, 0.45F, 0.45F, 1.0F);
        // Armado: tinte rojo PERMANENTE, no solo en hover — el jugador tiene que ver que se
        // quedó a mitad de confirmar aunque quite el ratón de encima del botón.
        else if (armed) g.setColor(1.6F, 0.55F, 0.55F, 1.0F);
        g.blit(tex, this.getX(), this.getY(), 0, 0, this.width, this.height, this.width, this.height);
        g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    @Override
    protected void updateWidgetNarration(@NotNull NarrationElementOutput out) {
        defaultButtonNarrationText(out);
    }
}
