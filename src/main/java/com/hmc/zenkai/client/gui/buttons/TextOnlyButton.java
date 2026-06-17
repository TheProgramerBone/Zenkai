package com.hmc.zenkai.client.gui.buttons;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Botón de solo texto — NUNCA dibuja el fondo gris de vanilla.
 * Si se le pasa una textura (ResourceLocation) la dibuja de fondo; si no,
 * solo renderiza el texto centrado. Tamaño estándar 60x20.
 */
public class TextOnlyButton extends AbstractButton {

    public static final int W = 60; // ancho estándar
    public static final int H = 30; // alto estándar

    private final Runnable onClick;
    @Nullable private final ResourceLocation texNormal; // fondo opcional (estado normal)
    @Nullable private final ResourceLocation texHover;  // fondo opcional (hover); si null usa texNormal

    private int colorNormal   = 0xFFFFFFFF; // blanco        → texto normal
    private int colorHover    = 0xFFFFE070; // dorado cálido → texto con el ratón encima
    private int colorInactive = 0xFFA0A0A0; // gris          → botón deshabilitado

    /** Botón de solo texto, tamaño estándar 60x30, sin textura. */
    public TextOnlyButton(int x, int y, Component message, Runnable onClick) {
        this(x, y, W, H, message, null, null, onClick);
    }

    /** Botón de solo texto con tamaño personalizado, sin textura. */
    public TextOnlyButton(int x, int y, int w, int h, Component message, Runnable onClick) {
        this(x, y, w, h, message, null, null, onClick);
    }

    /** Botón con textura de fondo opcional (normal + hover). Cualquiera puede ser null. */
    public TextOnlyButton(int x, int y, int w, int h, Component message,
                          @Nullable ResourceLocation texNormal,
                          @Nullable ResourceLocation texHover,
                          Runnable onClick) {
        super(x, y, w, h, message);
        this.onClick   = Objects.requireNonNull(onClick);
        this.texNormal = texNormal;
        this.texHover  = texHover;
    }

    /** Ajusta los colores del texto sin tocar la clase. Devuelve this para encadenar. */
    public TextOnlyButton textColors(int normal, int hover, int inactive) {
        this.colorNormal   = normal;
        this.colorHover    = hover;
        this.colorInactive = inactive;
        return this;
    }

    @Override
    public void onPress() {
        onClick.run();
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        boolean hovered = this.isMouseOver(mouseX, mouseY);

        // Fondo: solo si se proporcionó textura (jamás el fondo gris de vanilla)
        ResourceLocation tex = (hovered && texHover != null) ? texHover : texNormal;
        if (tex != null) {
            g.blit(tex, getX(), getY(), 0, 0, getWidth(), getHeight(), getWidth(), getHeight());
        }

        // Texto centrado (con sombra, como el resto de valores)
        int color = !this.active ? colorInactive : (hovered ? colorHover : colorNormal);
        var font = Minecraft.getInstance().font;
        g.drawCenteredString(font, getMessage(),
                getX() + getWidth() / 2,
                getY() + (getHeight() - 8) / 2,
                color);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput out) {
        defaultButtonNarrationText(out);
    }
}