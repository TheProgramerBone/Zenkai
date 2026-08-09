package com.hmc.zenkai.client.gui.widgets;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.gui.ZenkaiPalette;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * Celda de las nueve posiciones de técnica.
 * Antes eran TextOnlyButton con Component.empty() y el contenido lo pintaba la pantalla ENCIMA,
 * en un bucle aparte del render. De ahí los dos defectos visibles: una celda vacía no existía
 * (solo se veía el número flotando) y el icono se dibujaba en la esquina de la celda sin
 * centrar, pisando el número (los iconos son 20x20 y la celda también, así que la ocupaban
 * entera). Aquí el marco, el icono y el número son el mismo widget, en el mismo orden: marco →
 * icono centrado → número en un canto con sombra.
 * TRES ESTADOS del atlas slot.png (60x20, tres celdas de 20x20 en horizontal):
 *   u=0  vacía     (marco marrón, hundida)
 *   u=20 hover     (marco dorado)
 *   u=40 ocupada   (marco naranja)
 * El icono lo dibuja un callback en vez de recibir un ResourceLocation: las dos fuentes de
 * iconos (TechniqueIcons y PhysicalIcons) tienen firmas propias y teñido distinto, y meter aquí
 * un switch sobre el tipo obligaría a este widget a conocer el sistema de técnicas entero.
 */
public class SlotCell extends AbstractButton {

    /** Lado de la celda. Coincide con la grid de 20x20 de technique_icons/physical_icons. */
    public static final int SIZE = 20;
    /** Separación estándar entre celdas de una fila. */
    public static final int GAP = 2;

    private static final ResourceLocation ATLAS =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/slot.png");
    private static final int ATLAS_W = 60, ATLAS_H = 20;
    /** Margen del marco: el icono se dibuja dentro para no comerse el borde. */
    private static final int INSET = 2;

    /** Dibuja el contenido de la celda dado el rect INTERIOR ya calculado. */
    @FunctionalInterface
    public interface IconPainter {
        void paint(GuiGraphics g, int x, int y, int size);
    }

    private final int number;                 // 1..9 mostrado en la esquina
    private final BooleanSupplier occupied;
    private final BooleanSupplier highlighted; // modo asignación activo: la celda invita a click
    private final Runnable onClick;
    private IconPainter icon;

    public SlotCell(int x, int y, int number, BooleanSupplier occupied,
                    BooleanSupplier highlighted, Runnable onClick) {
        super(x, y, SIZE, SIZE, Component.literal(String.valueOf(number)));
        this.number = number;
        this.occupied = occupied;
        this.highlighted = highlighted;
        this.onClick = Objects.requireNonNull(onClick);
    }

    /** Se reasigna cada frame desde la pantalla: el ocupante de una posición cambia con el sync. */
    public SlotCell icon(IconPainter painter) {
        this.icon = painter;
        return this;
    }

    @Override
    public void onPress() { onClick.run(); }

    /** Rect interior donde va el icono. Público para que la pantalla pueda medir tooltips. */
    public int innerSize() { return SIZE - INSET * 2; }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        boolean occ = occupied.getAsBoolean();
        boolean hov = isMouseOver(mouseX, mouseY);

        int u = hov ? SIZE : (occ ? SIZE * 2 : 0);
        g.blit(ATLAS, getX(), getY(), u, 0, SIZE, SIZE, ATLAS_W, ATLAS_H);

        // Pulso del modo asignación: sin esto, "elige una posición" era un texto que aparecía
        // debajo y las celdas no cambiaban en absoluto, así que no se sabía dónde soltar.
        if (highlighted.getAsBoolean() && !occ) {
            g.fill(getX() + 1, getY() + 1, getX() + SIZE - 1, getY() + SIZE - 1,
                    ZenkaiPalette.SELECT_VEIL);
        }

        if (icon != null) {
            icon.paint(g, getX() + INSET, getY() + INSET, innerSize());
        }

        // El número va SIEMPRE con sombra y en la esquina superior izquierda, encima del icono:
        // es la referencia de la tecla, y si se pierde bajo el arte la barra no sirve de nada.
        g.drawString(Minecraft.getInstance().font, String.valueOf(number),
                getX() + 2, getY() + 2,
                occ ? ZenkaiPalette.TEXT : ZenkaiPalette.TEXT_DIM, true);
    }

    @Override
    protected void updateWidgetNarration(@NotNull NarrationElementOutput out) {
        defaultButtonNarrationText(out);
    }
}