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
 * Badge redondo "▶" (círculo teal + triángulo blanco) de previsualizar sonido —
 * TechniqueEditScreen lo usa debajo de las filas "Charge Sound"/"Fire Sound" para escuchar el
 * sonido elegido sin salir del editor. Pedido explícito del usuario con una imagen de
 * referencia: un badge con sombreado suave estilo `textures/gui/icons.png`, NO la familia de
 * bisel plano de btn_x/btn_pencil que usa el resto de iconos de fila del editor — por eso este
 * botón blitea la celda (60,120) del atlas compartido (`tools/gen_play_badge_icon.py`) en vez
 * de tener su propio par de PNG dedicados.
 * Dibujado ESCALADO (celda nativa de 20px reducida al vuelo a {@link #SIZE}, mismo truco de
 * blit con tamaño de destino != tamaño de origen que ya usa `TechniqueIcons.blit`): a 20px
 * nativos el badge no cabe en el hueco que tiene esta fila del editor sin reordenar media
 * pantalla, y reducirlo en pantalla no pierde nitidez porque el downscale del propio juego hace
 * ese trabajo. Sin textura de hover propia (el atlas no tiene variante _highlight): el brillo al
 * pasar el ratón sale de teñir el propio blit, mismo lenguaje que ya usa AtlasIconButton para
 * cualquier otro icono suelto de este atlas.
 */
public class PlayIconButton extends AbstractButton {

    public static final int SIZE = 12;

    private static final ResourceLocation ATLAS =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/icons.png");
    private static final int ATLAS_W = 256;
    private static final int ATLAS_H = 256;
    private static final int CELL = 20;
    private static final int U = 60, V = 120;

    private final Runnable onClick;

    public PlayIconButton(int x, int y, Runnable onClick) {
        super(x, y, SIZE, SIZE, Component.empty());
        this.onClick = Objects.requireNonNull(onClick);
    }

    @Override
    public void onPress() { onClick.run(); }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (!this.active) g.setColor(0.55F, 0.55F, 0.55F, 1.0F);
        else if (isHoveredOrFocused()) g.setColor(1.15F, 1.15F, 1.15F, 1.0F);
        g.blit(ATLAS, this.getX(), this.getY(), SIZE, SIZE,
                (float) U, (float) V, CELL, CELL, ATLAS_W, ATLAS_H);
        g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    @Override
    protected void updateWidgetNarration(@NotNull NarrationElementOutput out) {}
}
