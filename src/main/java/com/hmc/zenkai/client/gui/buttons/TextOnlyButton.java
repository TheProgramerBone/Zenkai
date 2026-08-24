package com.hmc.zenkai.client.gui.buttons;

import net.minecraft.client.Minecraft;
import com.hmc.zenkai.client.gui.ZenkaiPalette;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
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

    // Valores por defecto pensados para FONDO OSCURO. Toda pantalla que ponga este botón sobre
    // el panel beige debe llamar a textColors(...) con la escala tierra — que es lo que hacen
    // ya las de técnicas y la de configuración. Se dejan en claro y no en tierra porque un
    // botón sin configurar sobre el mundo tiene que verse; sobre el beige, al menos, canta lo
    // suficiente como para que se note que falta configurarlo.
    private int colorNormal   = ZenkaiPalette.TEXT;
    private int colorHover    = ZenkaiPalette.TEXT_HOVER;
    private int colorInactive = ZenkaiPalette.TEXT_OFF;

    /**
     * Sombra bajo el texto. Por defecto sí, porque por defecto los colores son los de fondo
     * oscuro y ahí la sombra es lo correcto.
     * La sombra y la escala de color van SIEMPRE juntas: un color tierra con sombra negra sobre
     * beige es un borrón, y un blanco sin sombra sobre el mundo desaparece. Por eso no se
     * exponen por separado — se usa onPanel() u onDark() y quedan las dos cosas hechas.
     */
    private boolean shadow = true;

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

    /**
     * Configura el botón para vivir SOBRE EL PANEL BEIGE: escala tierra y sin sombra.
     * Una sola forma para las dos cosas a propósito. Cuando eran ajustes independientes se
     * ponían los colores tierra y se olvidaba la sombra, que es exactamente lo que pasó en las
     * pestañas de técnicas: "Assign" y "Edit" en color correcto y con sombra negra debajo.
     */
    public TextOnlyButton onPanel() {
        this.colorNormal   = ZenkaiPalette.LABEL_ON_PANEL;
        this.colorHover    = ZenkaiPalette.DENIED_ON_PANEL;
        this.colorInactive = ZenkaiPalette.MUTED_ON_PANEL;
        this.shadow = false;
        return this;
    }

    /** Explícito para fondo oscuro. Es el estado por defecto; existe para poder volver a él. */
    public TextOnlyButton onDark() {
        this.colorNormal   = ZenkaiPalette.TEXT;
        this.colorHover    = ZenkaiPalette.TEXT_HOVER;
        this.colorInactive = ZenkaiPalette.TEXT_OFF;
        this.shadow = true;
        return this;
    }

    /** Colores a medida. Apaga la sombra si los tres son de la escala tierra. */
    public TextOnlyButton noShadow() {
        this.shadow = false;
        return this;
    }

    /**
     * Marca este botón como ACCIÓN: negrita y corchetes.
     * Un TextOnlyButton sin textura es texto suelto, y sobre una fila que ya lleva nombre y
     * ficha técnica no hay nada que lo distinga de una etiqueta más. Los corchetes son el
     * lenguaje que el propio juego usa para lo pulsable en los libros escritos, y la negrita
     * lo separa del texto de datos, que va en regular. Juntos convierten "Assign" en algo que
     * se lee como un control sin necesidad de dibujarle un marco que competiría con el panel.
     */
    public TextOnlyButton asAction() {
        this.action = true;
        return this;
    }

    private boolean action = false;

    /**
     * Marca este botón como SELECCIONADO: dibuja una barra fina bajo el texto en vez de
     * cambiar el label. asAction() (corchetes + negrita) ensancha el texto, y en un botón
     * angosto (los 45px de Stats/Progress en StatsScreen) el label ensanchado desborda su
     * propio ancho y se solapa con el botón de al lado — justo lo que pasaba antes de este
     * método. La barra no cambia el ancho del texto, así que nunca desborda.
     * Mismo lenguaje visual que TabIconButton.selected (barra bajo el ícono activo).
     */
    public TextOnlyButton selected(boolean selected) {
        this.selected = selected;
        return this;
    }

    private boolean selected = false;

    @Override
    protected void renderWidget(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        boolean hovered = this.isMouseOver(mouseX, mouseY);

        // Fondo: solo si se proporcionó textura (jamás el fondo gris de vanilla)
        ResourceLocation tex = (hovered && texHover != null) ? texHover : texNormal;
        if (tex != null) {
            g.blit(tex, getX(), getY(), 0, 0, getWidth(), getHeight(), getWidth(), getHeight());
        }

        // Texto centrado. drawCenteredString NO sirve aquí: dibuja sombra siempre y no tiene
        // parámetro para quitarla, así que los botones sobre el panel beige salían con una
        // sombra negra bajo un color tierra.
        int color = !this.active ? colorInactive : (hovered ? colorHover : colorNormal);
        var font = Minecraft.getInstance().font;
        Component msg = action
                ? Component.literal("[ ").append(getMessage()).append(" ]")
                .withStyle(net.minecraft.ChatFormatting.BOLD)
                : getMessage();
        g.drawString(font, msg,
                getX() + getWidth() / 2 - font.width(msg) / 2,
                getY() + (getHeight() - 8) / 2,
                color, shadow);

        if (selected) {
            g.fill(getX() + 4, getY() + getHeight() - 2, getX() + getWidth() - 4, getY() + getHeight() - 1,
                    ZenkaiPalette.GOLD);
        }
    }

    @Override
    protected void updateWidgetNarration(@NotNull NarrationElementOutput out) {
        defaultButtonNarrationText(out);
    }
}