package com.hmc.zenkai.client.gui.buttons;

import com.hmc.zenkai.Zenkai;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * Interruptor de fuego amigo de PartyScreen: ícono de textures/gui/icons.png con DOS estados
 * pintados a mano (activado/desactivado) en vez de btn_wide.png + texto ON/OFF — un dibujo
 * distinto por estado se lee de un vistazo sin depender del tooltip, y evita otro marco
 * naranja/beige compitiendo con el del propio panel (ver la nota sobre btn_wide dentro de
 * common_screen en el javadoc de PartyScreen).
 * Celdas reservadas en el atlas (256x256, grid de 20px, mismo esquema que ZenkaiTab):
 * (0,60) = OFF, (20,60) = ON. La fila v=60 estaba libre por completo antes de esto.
 */
public class FriendlyFireIconButton extends AbstractButton {

    private static final ResourceLocation ATLAS =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/icons.png");
    private static final int ATLAS_W = 256;
    private static final int ATLAS_H = 256;
    private static final int SIZE = 20;

    private static final int OFF_U = 0,  OFF_V = 60;
    private static final int ON_U  = 20, ON_V  = 60;

    private final BooleanSupplier friendlyFireOn;
    private final Runnable onClick;

    public FriendlyFireIconButton(int x, int y, BooleanSupplier friendlyFireOn, Runnable onClick) {
        super(x, y, SIZE, SIZE, Component.empty());
        this.friendlyFireOn = friendlyFireOn;
        this.onClick = Objects.requireNonNull(onClick);
    }

    @Override
    public void onPress() { onClick.run(); }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        boolean on = friendlyFireOn.getAsBoolean();
        int u = on ? ON_U : OFF_U;
        int v = on ? ON_V : OFF_V;
        if (isHoveredOrFocused()) g.setColor(1.2F, 1.2F, 1.2F, 1.0F);
        g.blit(ATLAS, getX(), getY(), u, v, SIZE, SIZE, ATLAS_W, ATLAS_H);
        g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    @Override
    protected void updateWidgetNarration(@NotNull NarrationElementOutput out) {
        defaultButtonNarrationText(out);
    }
}
