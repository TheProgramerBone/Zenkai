package com.hmc.zenkai.client.gui.buttons;

import com.hmc.zenkai.Zenkai;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Botón «Enterrar» del menú del cuerpo: una losa de piedra con una lápida sobre la hierba
 * ({@code tools/gen_body_bury_button.py}), para que no sea un botón de vanilla. La textura trae los tres estados apilados
 * (normal, resaltado, desactivado) a 64x16 y se dibuja a su tamaño; el texto va centrado en el hueco que deja la lápida.
 */
public class BodyBuryButton extends AbstractButton {
    public static final int WIDTH = 64, HEIGHT = 16;

    private static final ResourceLocation TEX =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/buttons/btn_bury.png");
    private static final int TEX_W = 64, TEX_H = 48;
    /** Ancho que ocupa la lápida a la izquierda: el texto se centra en lo que queda. */
    private static final int ICON_W = 16;

    private final Runnable onClick;

    public BodyBuryButton(int x, int y, Component label, Runnable onClick) {
        super(x, y, WIDTH, HEIGHT, label);
        this.onClick = Objects.requireNonNull(onClick);
    }

    @Override
    public void onPress() { onClick.run(); }

    @Override
    protected void renderWidget(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int v = !active ? 2 * HEIGHT : isHoveredOrFocused() ? HEIGHT : 0;
        g.blit(TEX, getX(), getY(), 0, v, WIDTH, HEIGHT, TEX_W, TEX_H);
        Minecraft mc = Minecraft.getInstance();
        int color = active ? (isHoveredOrFocused() ? 0xFFFFFFA0 : 0xFFFFFFFF) : 0xFFA0A0A0;
        int tw = mc.font.width(getMessage());
        g.drawString(mc.font, getMessage(), getX() + ICON_W + (WIDTH - ICON_W - tw) / 2,
                getY() + (HEIGHT - 8) / 2, color, true);
    }

    @Override
    protected void updateWidgetNarration(@NotNull NarrationElementOutput out) {
        defaultButtonNarrationText(out);
    }
}
