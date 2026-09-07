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
 * Candado de "desbloquear esto" (hoy: el tipo de técnica del editor de ki).
 *
 * DOS ESTADOS DE TEXTURA, como la papelera: normal y hover. El hover no es el mismo dibujo
 * aclarado — es el candado ABIERTO (ver tools/gen_lock_icon.py), así que el propio icono
 * enseña qué hace pulsarlo.
 *
 * El TERCER estado —no puedes pagarlo— NO es un PNG: se pinta oscureciendo el normal, mismo
 * truco que PlusIconButton usa para "sin TP". Un candado apagado se lee como "aún no", que es
 * exactamente lo que significa, y evita mantener un asset más sincronizado con los otros dos.
 *
 * OJO CON EL TOOLTIP: un widget inactivo NO enseña su Tooltip.create(), y aquí el tooltip
 * (cuánto cuesta y qué te falta) es justo lo que hace falta leer cuando el botón está apagado.
 * La pantalla que lo coloque tiene que dibujarlo A MANO al pasar el ratón —ver
 * TechniqueEditScreen.render y drawHoverTips de TechniquesScreen, que ya arrastraban este
 * mismo problema.
 */
public class LockIconButton extends AbstractButton {

    /** Lado del icono. La textura es 16x16 y se dibuja 1:1: sin escalado no hay medio píxel. */
    public static final int SIZE = 16;

    private static final ResourceLocation TEX_NORMAL =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/btn_lock.png");
    private static final ResourceLocation TEX_HOVER =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/btn_lock_highlight.png");

    private final Runnable onClick;

    public LockIconButton(int x, int y, Runnable onClick) {
        super(x, y, SIZE, SIZE, Component.empty());
        this.onClick = Objects.requireNonNull(onClick);
    }

    @Override
    public void onPress() {
        onClick.run();
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        ResourceLocation tex = (this.active && this.isHoveredOrFocused()) ? TEX_HOVER : TEX_NORMAL;
        if (!this.active) g.setColor(0.45F, 0.45F, 0.45F, 1.0F);
        g.blit(tex, this.getX(), this.getY(), 0, 0, SIZE, SIZE, SIZE, SIZE);
        if (!this.active) g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    @Override
    protected void updateWidgetNarration(@NotNull NarrationElementOutput out) {
        defaultButtonNarrationText(out);
    }
}
